//! Vending Machine — the classic state-machine LLD problem.
//!
//! # The scenario
//!
//! A punter inserts coins to build a balance, then selects a slot. If the product is in stock and the
//! balance covers its price, the machine dispenses it *and* the correct change from its coin float,
//! then resets. Otherwise it returns a precise error — sold out, not enough money, or "exact change
//! only" when it physically cannot make the change.
//!
//! # The Rust idioms
//!
//! - **A state machine over a running balance + inventory + coin float.** Rather than an explicit
//!   `enum State`, the machine's state is the escrow of inserted coins plus the stock and float; a
//!   purchase is a transition that either succeeds (dispense + change + reset) or leaves everything
//!   untouched. `select` is a `match`/guard cascade that fails *before* mutating anything.
//! - **A data-carrying error `enum`.** [`VendError::InsufficientFunds`] reports `needed` and `balance`
//!   so the caller can react — errors are values that carry context, not just tags.
//! - **Greedy change-making** over the canonical `5/10/25` denominations, respecting the float's
//!   supply. Note the subtlety: greedy is optimal for canonical denominations with *unlimited* supply,
//!   but a *limited* float can make it fail where a solution exists (took a quarter, then can't make
//!   the last 5). This machine, like real ones, then reports `ExactChangeOnly`; the robust fix is a
//!   coin-change DP (the extension).
//!
//! # Alternatives
//!
//! The **typestate** pattern encodes the machine's phases as distinct types so you couldn't, say,
//! `select` before inserting — illegal calls become compile errors. It's elegant but rigid; most real
//! machines keep a runtime model (as here) for a dynamic UI and persistence.

use std::collections::HashMap;

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
    /// Unknown slot, or the slot is out of stock.
    SoldOut,
    /// The inserted balance does not cover the price.
    InsufficientFunds { needed: u32, balance: u32 },
    /// The product is affordable but the float can't make the required change.
    ExactChangeOnly,
}

/// A successful sale: the product and the change returned.
#[derive(Debug, PartialEq, Eq)]
pub struct Dispensed {
    pub product: String,
    pub change: Vec<Coin>,
}

struct Slot {
    product: String,
    price: u32,
    qty: u32,
}

/// A vending machine: stocked slots, a coin float for change, and the coins currently inserted.
pub struct VendingMachine {
    slots: HashMap<String, Slot>,
    float: HashMap<Coin, u32>,
    inserted: Vec<Coin>,
}

impl VendingMachine {
    pub fn new() -> Self {
        VendingMachine {
            slots: HashMap::new(),
            float: HashMap::new(),
            inserted: Vec::new(),
        }
    }

    /// Stock (or restock) a slot with a product, price, and quantity.
    pub fn stock(&mut self, slot: &str, product: &str, price_cents: u32, qty: u32) {
        self.slots.insert(
            slot.to_string(),
            Slot {
                product: product.to_string(),
                price: price_cents,
                qty,
            },
        );
    }

    /// Seed the machine's change float (operator loading coins).
    pub fn add_coins(&mut self, coins: &[Coin]) {
        for &c in coins {
            *self.float.entry(c).or_insert(0) += 1;
        }
    }

    /// Insert a coin toward the current balance.
    pub fn insert(&mut self, coin: Coin) {
        self.inserted.push(coin);
    }

    /// The currently inserted balance, in cents.
    pub fn balance(&self) -> u32 {
        self.inserted.iter().map(|c| c.cents()).sum()
    }

    /// Attempt to buy the product in `slot`. On success, dispenses it plus change and resets the
    /// balance; on any error, nothing is mutated.
    pub fn select(&mut self, slot: &str) -> Result<Dispensed, VendError> {
        let (price, product) = match self.slots.get(slot) {
            Some(s) if s.qty > 0 => (s.price, s.product.clone()),
            _ => return Err(VendError::SoldOut),
        };

        let balance = self.balance();
        if balance < price {
            return Err(VendError::InsufficientFunds {
                needed: price,
                balance,
            });
        }

        // The inserted coins join the float for the purpose of making change.
        let mut pool = self.float.clone();
        for &c in &self.inserted {
            *pool.entry(c).or_insert(0) += 1;
        }
        let change = match make_change(balance - price, &pool) {
            Some(change) => change,
            None => return Err(VendError::ExactChangeOnly),
        };

        // Commit: the float keeps everything minus the change handed back.
        for c in &change {
            *pool.get_mut(c).unwrap() -= 1;
        }
        self.float = pool;
        self.inserted.clear();
        if let Some(s) = self.slots.get_mut(slot) {
            s.qty -= 1;
        }
        Ok(Dispensed { product, change })
    }

    /// Return the inserted coins and reset the balance to zero.
    pub fn refund(&mut self) -> Vec<Coin> {
        std::mem::take(&mut self.inserted)
    }
}

impl Default for VendingMachine {
    fn default() -> Self {
        Self::new()
    }
}

/// Make exactly `amount` cents from `pool`, greedily largest-first. `None` if it can't be done.
fn make_change(mut amount: u32, pool: &HashMap<Coin, u32>) -> Option<Vec<Coin>> {
    let mut avail = pool.clone();
    let mut out = Vec::new();
    for &coin in &[Coin::Quarter, Coin::Dime, Coin::Nickel] {
        let value = coin.cents();
        while amount >= value && avail.get(&coin).copied().unwrap_or(0) > 0 {
            amount -= value;
            *avail.get_mut(&coin).unwrap() -= 1;
            out.push(coin);
        }
    }
    (amount == 0).then_some(out)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn buys_a_product_with_exact_money() {
        let mut m = VendingMachine::new();
        m.stock("A1", "Cola", 25, 3);
        m.insert(Coin::Quarter);
        let d = m.select("A1").unwrap();
        assert_eq!(d.product, "Cola");
        assert_eq!(d.change, vec![]); // exact
        assert_eq!(m.balance(), 0);
    }

    #[test]
    fn gives_change() {
        let mut m = VendingMachine::new();
        m.stock("A1", "Water", 15, 1);
        m.add_coins(&[Coin::Dime, Coin::Nickel]);
        m.insert(Coin::Quarter); // balance 25, needs 15 → change 10
        let d = m.select("A1").unwrap();
        assert_eq!(d.change, vec![Coin::Dime]);
    }

    #[test]
    fn change_uses_multiple_coins() {
        let mut m = VendingMachine::new();
        m.stock("A1", "Chips", 10, 1);
        m.add_coins(&[Coin::Dime, Coin::Nickel]);
        m.insert(Coin::Quarter); // change 15 → dime + nickel
        let d = m.select("A1").unwrap();
        assert_eq!(d.change, vec![Coin::Dime, Coin::Nickel]);
    }

    #[test]
    fn insufficient_funds_does_not_dispense() {
        let mut m = VendingMachine::new();
        m.stock("A1", "Cola", 25, 1);
        m.insert(Coin::Dime); // 10 < 25
        assert_eq!(
            m.select("A1"),
            Err(VendError::InsufficientFunds {
                needed: 25,
                balance: 10
            })
        );
        assert_eq!(m.balance(), 10); // intact
                                     // still in stock — buy it properly
        m.insert(Coin::Dime);
        m.insert(Coin::Nickel);
        assert!(m.select("A1").is_ok());
    }

    #[test]
    fn unknown_and_empty_slots_are_sold_out() {
        let mut m = VendingMachine::new();
        assert_eq!(m.select("NOPE"), Err(VendError::SoldOut));

        m.stock("A1", "Cola", 5, 1);
        m.insert(Coin::Nickel);
        assert!(m.select("A1").is_ok());
        m.insert(Coin::Nickel);
        assert_eq!(m.select("A1"), Err(VendError::SoldOut)); // qty exhausted
    }

    #[test]
    fn exact_change_only_when_float_cannot_make_change() {
        let mut m = VendingMachine::new();
        m.stock("A1", "Gum", 20, 1);
        m.insert(Coin::Quarter); // balance 25, change 5, but the only coin available is the quarter
        assert_eq!(m.select("A1"), Err(VendError::ExactChangeOnly));
        assert_eq!(m.balance(), 25); // nothing mutated
    }

    #[test]
    fn refund_returns_inserted_coins() {
        let mut m = VendingMachine::new();
        m.insert(Coin::Quarter);
        m.insert(Coin::Dime);
        assert_eq!(m.balance(), 35);
        let refunded = m.refund();
        assert_eq!(refunded, vec![Coin::Quarter, Coin::Dime]);
        assert_eq!(m.balance(), 0);
    }
}
