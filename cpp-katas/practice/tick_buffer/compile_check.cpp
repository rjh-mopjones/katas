// Proves the skeleton header compiles (its bodies throw at runtime; this only checks the build).
// Not linked into any executable — the practice_compile OBJECT target just compiles it.
#include "tick_buffer.hpp"

// Force instantiation of every member so the skeleton's bodies are type-checked.
template class katas::TickBuffer<int>;
