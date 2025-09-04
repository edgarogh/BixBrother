//! JSON-deserializing structures for the GBFS data

use serde::{Deserialize, Deserializer};
use std::str::FromStr;
use uuid::Uuid;

#[derive(Debug, Deserialize)]
pub struct StationCollectionObj<S> {
    pub last_updated: u64,
    pub ttl: u64,
    pub data: StationCollectionDataObj<S>,
}

#[derive(Debug, Deserialize)]
pub struct StationCollectionDataObj<S> {
    pub stations: Vec<S>,
}

#[derive(Debug, Deserialize)]
pub struct StationInfoObj<Str> {
    #[serde(deserialize_with = "deserialize_from_str")]
    pub station_id: u16,
    pub external_id: Uuid,
    pub name: Str,
    pub short_name: Str,
    pub lat: f32,
    pub lon: f32,
    pub capacity: u16,
    pub has_kiosk: bool,
}

#[derive(Debug, Deserialize)]
pub struct StationStatusObj {
    #[serde(deserialize_with = "deserialize_from_str")]
    pub station_id: u16,
    pub num_bikes_available: u16,
    pub num_ebikes_available: u16,
    pub num_bikes_disabled: u16,
    pub num_docks_available: u16,
    pub num_docks_disabled: u16,
    pub last_reported: u64,
}

fn deserialize_from_str<'de, D: Deserializer<'de>, T: FromStr>(de: D) -> Result<T, D::Error>
where
    <T as FromStr>::Err: std::fmt::Display,
{
    let str = <&str>::deserialize(de)?;
    str.parse().map_err(serde::de::Error::custom)
}
