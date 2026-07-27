//! Parking Lot — the "hello world" of low-level design.
//!
//! # The scenario
//!
//! A lot has spots of three sizes (Small, Medium, Large). Vehicles of three kinds park in them under
//! *fit* rules — a motorcycle fits anywhere, a car needs at least a medium, a truck needs a large —
//! and we allocate **best-fit** (the smallest spot that fits, so big vehicles don't starve later).
//! Parking returns a [`Ticket`]; returning the ticket frees the exact spot.
//!
//! # The Rust idioms
//!
//! - **Enums + exhaustive `match`.** [`VehicleKind`] and [`SpotKind`] are enums; the fit rules are a
//!   `match` on the vehicle kind returning the spot kinds it may use, in best-fit order. Because the
//!   `match` is exhaustive, adding a new vehicle kind won't compile until you say where it parks —
//!   the compiler keeps the rules honest.
//! - **Best-fit** falls out of trying the spot kinds in ascending order and taking the first free one.
//! - **The ticket is a capability.** [`Ticket`] carries exactly what's needed to free the spot
//!   (its kind, index, and plate). `unpark` consumes the ticket by value, and a bogus or already-used
//!   ticket is rejected — you can only free a spot you hold the receipt for.
//!
//! # Alternatives
//!
//! The classic OO answer models `Vehicle`/`Spot` as a trait-object hierarchy; in Rust the closed set
//! of kinds is better as enums (no allocation, exhaustive matching). Extensions: multiple floors, and
//! a thread-safe lot (`Mutex`) where two cars race for the last spot.

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
    /// No fitting spot was free.
    Full,
    /// The ticket does not match an occupied spot (bogus or already returned).
    UnknownTicket,
}

/// The spot kinds a vehicle may use, smallest (best-fit) first.
fn fit_order(kind: VehicleKind) -> &'static [SpotKind] {
    match kind {
        VehicleKind::Motorcycle => &[SpotKind::Small, SpotKind::Medium, SpotKind::Large],
        VehicleKind::Car => &[SpotKind::Medium, SpotKind::Large],
        VehicleKind::Truck => &[SpotKind::Large],
    }
}

/// A single-level parking lot with a fixed number of spots per size.
pub struct ParkingLot {
    // spots[kind as usize][i] == Some(plate) when occupied.
    spots: [Vec<Option<String>>; 3],
}

impl ParkingLot {
    /// Build a lot with the given number of spots of each size.
    pub fn new(small: usize, medium: usize, large: usize) -> Self {
        ParkingLot {
            spots: [vec![None; small], vec![None; medium], vec![None; large]],
        }
    }

    /// Park `vehicle` in the smallest free spot that fits, returning a ticket.
    pub fn park(&mut self, vehicle: Vehicle) -> Result<Ticket, ParkError> {
        for &spot_kind in fit_order(vehicle.kind) {
            let row = &mut self.spots[spot_kind as usize];
            if let Some(index) = row.iter().position(Option::is_none) {
                row[index] = Some(vehicle.plate.clone());
                return Ok(Ticket {
                    spot_kind,
                    index,
                    plate: vehicle.plate,
                });
            }
        }
        Err(ParkError::Full)
    }

    /// Free the spot named by `ticket`. The ticket must match an occupied spot.
    pub fn unpark(&mut self, ticket: Ticket) -> Result<(), ParkError> {
        let row = self
            .spots
            .get_mut(ticket.spot_kind as usize)
            .ok_or(ParkError::UnknownTicket)?;
        let slot = row.get_mut(ticket.index).ok_or(ParkError::UnknownTicket)?;
        if slot.as_deref() == Some(ticket.plate.as_str()) {
            *slot = None;
            Ok(())
        } else {
            Err(ParkError::UnknownTicket)
        }
    }

    /// The number of free spots of a given size.
    pub fn available(&self, kind: SpotKind) -> usize {
        self.spots[kind as usize]
            .iter()
            .filter(|s| s.is_none())
            .count()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn vehicle(kind: VehicleKind, plate: &str) -> Vehicle {
        Vehicle {
            kind,
            plate: plate.to_string(),
        }
    }

    #[test]
    fn best_fit_puts_each_vehicle_in_its_smallest_spot() {
        let mut lot = ParkingLot::new(1, 1, 1);

        let m = lot.park(vehicle(VehicleKind::Motorcycle, "M1")).unwrap();
        assert_eq!(m.spot_kind, SpotKind::Small);
        let c = lot.park(vehicle(VehicleKind::Car, "C1")).unwrap();
        assert_eq!(c.spot_kind, SpotKind::Medium);
        let t = lot.park(vehicle(VehicleKind::Truck, "T1")).unwrap();
        assert_eq!(t.spot_kind, SpotKind::Large);
    }

    #[test]
    fn motorcycle_spills_up_when_small_is_full() {
        let mut lot = ParkingLot::new(1, 1, 0);
        let a = lot.park(vehicle(VehicleKind::Motorcycle, "M1")).unwrap();
        assert_eq!(a.spot_kind, SpotKind::Small);
        let b = lot.park(vehicle(VehicleKind::Motorcycle, "M2")).unwrap();
        assert_eq!(b.spot_kind, SpotKind::Medium); // small full → next size up
    }

    #[test]
    fn car_never_uses_a_small_spot() {
        let mut lot = ParkingLot::new(5, 0, 0);
        assert_eq!(
            lot.park(vehicle(VehicleKind::Car, "C1")),
            Err(ParkError::Full)
        );
    }

    #[test]
    fn truck_needs_a_large_spot() {
        let mut lot = ParkingLot::new(2, 2, 0);
        assert_eq!(
            lot.park(vehicle(VehicleKind::Truck, "T1")),
            Err(ParkError::Full)
        );
    }

    #[test]
    fn unpark_frees_the_spot() {
        let mut lot = ParkingLot::new(1, 0, 0);
        let ticket = lot.park(vehicle(VehicleKind::Motorcycle, "M1")).unwrap();
        assert_eq!(lot.available(SpotKind::Small), 0);
        lot.unpark(ticket.clone()).unwrap();
        assert_eq!(lot.available(SpotKind::Small), 1);
        // Returning the same ticket again is rejected.
        assert_eq!(lot.unpark(ticket), Err(ParkError::UnknownTicket));
    }

    #[test]
    fn availability_tracks_occupancy() {
        let mut lot = ParkingLot::new(2, 0, 0);
        assert_eq!(lot.available(SpotKind::Small), 2);
        lot.park(vehicle(VehicleKind::Motorcycle, "M1")).unwrap();
        assert_eq!(lot.available(SpotKind::Small), 1);
        lot.park(vehicle(VehicleKind::Motorcycle, "M2")).unwrap();
        assert_eq!(lot.available(SpotKind::Small), 0);
        assert_eq!(
            lot.park(vehicle(VehicleKind::Motorcycle, "M3")),
            Err(ParkError::Full)
        );
    }
}
