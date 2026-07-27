/// The kinds of vehicle the lot accepts.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum VehicleKind {
    Motorcycle,
    Car,
    Truck,
}

/// Spot sizes, ordered `Small < Medium < Large`.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub enum SpotKind {
    Small,
    Medium,
    Large,
}

/// A vehicle wanting to park.
#[derive(Debug, Clone)]
pub struct Vehicle {
    pub kind: VehicleKind,
    pub plate: String,
}

/// A parking receipt — the capability to free the exact spot it names.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Ticket {
    pub spot_kind: SpotKind,
    pub index: usize,
    pub plate: String,
}

/// Why an operation failed.
#[derive(Debug, PartialEq, Eq)]
pub enum ParkError {
    Full,
    UnknownTicket,
}

/// A single-level parking lot. You design the internals (spots per size + occupancy).
pub struct ParkingLot;

impl ParkingLot {
    pub fn new(_small: usize, _medium: usize, _large: usize) -> Self {
        todo!()
    }

    pub fn park(&mut self, _vehicle: Vehicle) -> Result<Ticket, ParkError> {
        todo!()
    }

    pub fn unpark(&mut self, _ticket: Ticket) -> Result<(), ParkError> {
        todo!()
    }

    pub fn available(&self, _kind: SpotKind) -> usize {
        todo!()
    }
}
