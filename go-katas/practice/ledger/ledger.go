package ledger

type Ledger struct{}

func New() *Ledger { panic("TODO: implement") }

func (l *Ledger) Open(id string, initial int64) error { panic("TODO: implement") }

func (l *Ledger) Balance(id string) (int64, error) { panic("TODO: implement") }

func (l *Ledger) Deposit(idemKey, id string, amount int64) error { panic("TODO: implement") }

func (l *Ledger) Withdraw(idemKey, id string, amount int64) error { panic("TODO: implement") }

func (l *Ledger) Transfer(idemKey, from, to string, amount int64) error { panic("TODO: implement") }

func (l *Ledger) Entries(id string) []Entry { panic("TODO: implement") }

func (l *Ledger) CheckInvariant() error { panic("TODO: implement") }
