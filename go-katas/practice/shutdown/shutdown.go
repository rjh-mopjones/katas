package shutdown

import (
	"context"
	"errors"
)

// ErrShuttingDown is returned by Submit once Shutdown has begun.
var ErrShuttingDown = errors.New("shutdown: server is shutting down")

// Job is a unit of work submitted to the server.
type Job struct {
	ID string
}

// Server accepts Jobs onto a bounded internal queue processed by a fixed worker
// pool, and supports graceful shutdown.
type Server struct{}

// NewServer returns a ready Server with the given number of workers, a jobs queue
// of the given capacity, and the per-job processing function.
func NewServer(workers, queueSize int, process func(Job)) *Server {
	panic("TODO: implement")
}

// Start launches the worker pool. Each worker ranges over the internal jobs
// channel until it is closed and drained.
func (s *Server) Start() {
	panic("TODO: implement")
}

// Submit enqueues j for processing. It returns ErrShuttingDown once Shutdown has
// been called, and must never panic by sending on a closed channel.
func (s *Server) Submit(j Job) error {
	panic("TODO: implement")
}

// Shutdown initiates graceful shutdown: stop accepting new jobs, drain in-flight
// and queued work, and block until done or until ctx is done.
func (s *Server) Shutdown(ctx context.Context) error {
	panic("TODO: implement")
}
