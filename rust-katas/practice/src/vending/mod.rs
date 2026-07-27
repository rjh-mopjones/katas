/// A coin, in cents.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum Coin {
    Nickel,
    Dime,
    Quarter,
}

impl Coin {
    /// The coin's value in cents.
    pub fn cents(self) -> u32 {
        match self {
            Coin::Nickel => 5,
            Coin::Dime => 10,
            Coin::Quarter => 25,
        }
    }
}

/// Why a selection failed.
#[derive(Debug, PartialEq, Eq)]
pub enum VendError {
    SoldOut,
    InsufficientFunds { needed: u32, balance: u32 },
    ExactChangeOnly,
}

/// A successful sale: the product and the change returned.
#[derive(Debug, PartialEq, Eq)]
pub struct Dispensed {
    pub product: String,
    pub change: Vec<Coin>,
}

/// A vending machine. You design the internals (slots, coin float, inserted balance).
pub struct VendingMachine;

impl VendingMachine {
    pub fn new() -> Self {
        todo!()
    }

    pub fn stock(&mut self, _slot: &str, _product: &str, _price_cents: u32, _qty: u32) {
        todo!()
    }

    pub fn add_coins(&mut self, _coins: &[Coin]) {
        todo!()
    }

    pub fn insert(&mut self, _coin: Coin) {
        todo!()
    }

    pub fn balance(&self) -> u32 {
        todo!()
    }

    pub fn select(&mut self, _slot: &str) -> Result<Dispensed, VendError> {
        todo!()
    }

    pub fn refund(&mut self) -> Vec<Coin> {
        todo!()
    }
}

impl Default for VendingMachine {
    fn default() -> Self {
        Self::new()
    }
}
