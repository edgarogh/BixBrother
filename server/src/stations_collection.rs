use std::collections::HashMap;
use uuid::Uuid;

pub trait StationLike {
    fn id(&self) -> u16;
    fn external_id(&self) -> Uuid;
}

#[derive(Clone)]
pub struct StationsCollection<T> {
    stations: Vec<T>,
    stations_by_id: HashMap<u16, usize>,
    stations_by_external_id: HashMap<Uuid, usize>,
}

impl<T: StationLike> FromIterator<T> for StationsCollection<T> {
    fn from_iter<I: IntoIterator<Item = T>>(iter: I) -> Self {
        let stations = Vec::from_iter(iter);

        let mut stations_by_id = HashMap::new();
        let mut stations_by_external_id = HashMap::new();
        for (idx, station) in stations.iter().enumerate() {
            stations_by_id.insert(station.id(), idx);
            stations_by_external_id.insert(station.external_id(), idx);
        }

        Self {
            stations,
            stations_by_id,
            stations_by_external_id,
        }
    }
}

impl<T: StationLike> StationsCollection<T> {
    pub fn get_by_id(&self, id: u16) -> Option<&T> {
        Some(&self.stations[*self.stations_by_id.get(&id)?])
    }

    pub fn get_by_id_mut(&mut self, id: u16) -> Option<&mut T> {
        Some(&mut self.stations[*self.stations_by_id.get(&id)?])
    }

    pub fn get_by_external_id(&self, id: Uuid) -> Option<&T> {
        Some(&self.stations[*self.stations_by_external_id.get(&id)?])
    }

    pub fn get_by_external_id_mut(&mut self, id: Uuid) -> Option<&mut T> {
        Some(&mut self.stations[*self.stations_by_external_id.get(&id)?])
    }
}

impl<'a, T> IntoIterator for &'a StationsCollection<T> {
    type Item = &'a T;
    type IntoIter = std::slice::Iter<'a, T>;

    fn into_iter(self) -> Self::IntoIter {
        self.stations.iter()
    }
}

impl<T> StationsCollection<T> {
    pub fn len(&self) -> usize {
        self.stations.len()
    }
}
