#[macro_use]
extern crate log;

use crate::fcm::FcmService;
use crate::gbfs::{StationCollectionObj, StationInfoObj, StationStatusObj};
use crate::stations_collection::{StationLike, StationsCollection};
use crate::thumbnailer::Thumbnailer;
use chrono::Utc;
use eyre::{Context, ContextCompat};
use reqwest::Client;
use rocket::fairing::{Fairing, Info, Kind};
use rocket::http::ContentType;
use rocket::response::content::RawJson;
use rocket::{Build, Orbit, Rocket, Shutdown, State, routes};
use std::collections::HashSet;
use std::sync::Arc;
use std::time::Duration;
use tokio::sync::OnceCell;
use uuid::Uuid;

mod fcm;
mod gbfs;
mod stations_collection;
mod thumbnailer;

struct StationInfo {
    id: u16,
    name: String,
    external_id: Uuid,
    lat: f32,
    lon: f32,
    capacity: u16,
    current_status: StationStatus,
}

impl StationLike for StationInfo {
    fn id(&self) -> u16 {
        self.id
    }

    fn external_id(&self) -> Uuid {
        self.external_id
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq, serde::Serialize)]
struct StationStatus {
    #[serde(rename = "b")]
    pub num_bikes_available: u16,
    #[serde(rename = "e")]
    pub num_ebikes_available: u16,
    #[serde(rename = "a")]
    pub num_docks_available: u16,
}

impl StationStatus {
    pub fn new_empty(capacity: u16) -> Self {
        Self {
            num_bikes_available: 0,
            num_ebikes_available: 0,
            num_docks_available: capacity,
        }
    }
}

#[derive(Clone, Copy, Debug, serde::Serialize)]
struct IdentifiedStationStatus<'a> {
    #[serde(rename = "i")]
    external_id: Uuid,

    #[serde(rename = "n")]
    name: &'a str,

    lat: f32,
    lon: f32,

    #[serde(flatten)]
    status: StationStatus,

    #[serde(rename = "c")]
    capacity: u16,
}

fn station_status_of_gbfs(obj: &StationStatusObj) -> StationStatus {
    StationStatus {
        num_bikes_available: obj.num_bikes_available,
        num_ebikes_available: obj.num_ebikes_available,
        num_docks_available: obj.num_docks_available,
    }
}

struct StationWatcher {
    recv: tokio::sync::watch::Receiver<Arc<str>>,
}

#[derive(Clone, Debug, serde::Serialize)]
struct StationsResponse<'a> {
    updated_at: i64,
    stations: Vec<IdentifiedStationStatus<'a>>,
}

async fn update_station_statuses(
    client: &Client,
    stations: &mut StationsCollection<StationInfo>,
) -> eyre::Result<Vec<u16>> {
    let mut changes = Vec::new();
    let station_status_raw = client
        .get("https://gbfs.velobixi.com/gbfs/fr/station_status.json")
        .send()
        .await
        .wrap_err("querying status")?
        .bytes()
        .await
        .wrap_err("reading status bytes")?;

    let station_status =
        serde_json::from_slice::<StationCollectionObj<StationStatusObj>>(&station_status_raw)
            .wrap_err("decoding status")?;

    for station in &station_status.data.stations {
        let new_status = station_status_of_gbfs(station);
        let current_status = &mut stations
            .get_by_id_mut(station.station_id)
            .wrap_err("no station with id")?
            .current_status;

        if *current_status != new_status {
            *current_status = new_status;
            changes.push(station.station_id);
        }
    }

    Ok(changes)
}

async fn fetch_loop(
    shutdown_signal: Shutdown,
    interval: Duration,
    station_info: Arc<StationCollectionObj<StationInfoObj<String>>>,
    updater: tokio::sync::watch::Sender<Arc<str>>,
) -> eyre::Result<()> {
    let mut fcm_client = FcmService::from_service_file().await?;

    let client = Client::new();

    let mut stations =
        StationsCollection::from_iter(station_info.data.stations.iter().map(|station_info| {
            StationInfo {
                id: station_info.station_id,
                name: station_info.name.clone(),
                external_id: station_info.external_id,
                lat: station_info.lat,
                lon: station_info.lon,
                capacity: station_info.capacity,
                current_status: StationStatus::new_empty(station_info.capacity),
            }
        }));

    let mut changes = update_station_statuses(&client, &mut stations).await?;

    info!("loop spinning");
    loop {
        let now = Utc::now().timestamp();

        let converted = stations
            .into_iter()
            .map(|s| IdentifiedStationStatus {
                external_id: s.external_id,
                name: &s.name,
                lat: s.lat,
                lon: s.lon,
                status: s.current_status,
                capacity: s.capacity,
            })
            .collect::<Vec<_>>();

        let stations_response = StationsResponse {
            updated_at: now,
            stations: converted,
        };

        updater
            .send(Arc::from(
                serde_json::to_string(&stations_response).unwrap(),
            ))
            .expect("channel closed");

        debug!("{} stations updated", stations.len());
        for change in changes {
            let station = stations.get_by_id(change).wrap_err("no station with id")?;
            trace!(
                "{}: b={} e={}",
                station.name,
                station.current_status.num_bikes_available,
                station.current_status.num_ebikes_available
            );

            fcm_client
                .send(station.external_id, station.current_status, now)
                .await
                .wrap_err("sending message to FCM")?;
        }

        tokio::select! {
            _ = shutdown_signal.clone() => break Ok(()),
            _ = tokio::time::sleep(interval) => (),
        }

        changes = update_station_statuses(&client, &mut stations).await?;
    }
}

struct FetchLoopFairing {
    send: OnceCell<tokio::sync::watch::Sender<Arc<str>>>,
}

impl FetchLoopFairing {
    pub fn new() -> Self {
        Self {
            send: OnceCell::new(),
        }
    }
}

#[rocket::async_trait]
impl Fairing for FetchLoopFairing {
    fn info(&self) -> Info {
        Info {
            name: "gbfs fetch loop",
            kind: Kind::Ignite | Kind::Liftoff,
        }
    }

    async fn on_ignite(&self, rocket: Rocket<Build>) -> rocket::fairing::Result {
        let (send, recv) = tokio::sync::watch::channel(Arc::from("[]"));
        self.send.set(send).unwrap();
        Ok(rocket.manage(StationWatcher { recv }))
    }

    async fn on_liftoff(&self, rocket: &Rocket<Orbit>) {
        let shutdown = rocket.shutdown();
        let station_collection = Arc::clone(rocket.state().unwrap());
        let send = self.send.get().unwrap().clone();
        tokio::task::spawn(async move {
            if let Err(err) = fetch_loop(
                shutdown.clone(),
                Duration::from_secs(10),
                station_collection,
                send,
            )
            .await
            {
                shutdown.notify();
                panic!("{err:?}");
            }
        });
    }
}

#[rocket::get("/statuses")]
fn get_statuses(stations: &State<StationWatcher>) -> RawJson<Arc<str>> {
    RawJson(Arc::clone(&stations.recv.borrow()))
}

#[rocket::get("/stations/<station_id>/map_thumbnail128.png")]
async fn get_station_map_thumbnail(
    thumbnailer: &State<Thumbnailer>,
    all_ids: &State<HashSet<Uuid>>,
    all_stations: &State<Arc<StationCollectionObj<StationInfoObj<String>>>>,
    station_id: Uuid,
) -> Result<Option<(ContentType, Vec<u8>)>, String> {
    if all_ids.contains(&station_id) {
        // TODO bad for perfs
        let station = all_stations
            .data
            .stations
            .iter()
            .find(|s| s.external_id == station_id)
            .unwrap();

        match thumbnailer.thumbnail(station.lat, station.lon).await {
            Ok(thumbnail) => Ok(Some((ContentType::PNG, thumbnail))),
            Err(err) => Err(err.to_string()),
        }
    } else {
        Ok(None)
    }
}

#[rocket::launch]
async fn launch() -> _ {
    let client = Client::new();

    let station_info = client
        .get("https://gbfs.velobixi.com/gbfs/fr/station_information.json")
        .send()
        .await
        .unwrap()
        .json::<StationCollectionObj<StationInfoObj<String>>>()
        .await
        .unwrap();

    let station_info = Arc::new(station_info);

    let all_ids = station_info
        .data
        .stations
        .iter()
        .map(|s| s.external_id)
        .collect::<HashSet<_>>();

    rocket::build()
        .manage(all_ids)
        .manage(station_info)
        .attach(FetchLoopFairing::new())
        .manage(Thumbnailer::new())
        .mount("/v1", routes![get_statuses, get_station_map_thumbnail])
}
