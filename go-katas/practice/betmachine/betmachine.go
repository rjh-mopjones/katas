package betmachine

import "errors"

type State int

const (
	StatePending State = iota
	StateAccepted
	StateSettled
	StateRejected
	StateCancelled
)

func (s State) String() string { panic("TODO: implement") }

type Event int

const (
	EventAccept Event = iota
	EventSettle
	EventReject
	EventCancel
)

func (e Event) String() string { panic("TODO: implement") }

var ErrUnknownBet = errors.New("betmachine: unknown bet")

var ErrIllegalTransition = errors.New("betmachine: illegal transition")

type BetMachine struct{}

func NewBetMachine() *BetMachine { panic("TODO: implement") }

func (m *BetMachine) Open(betID string) error { panic("TODO: implement") }

func (m *BetMachine) Apply(betID string, e Event) (State, error) { panic("TODO: implement") }

func (m *BetMachine) State(betID string) (State, bool) { panic("TODO: implement") }
