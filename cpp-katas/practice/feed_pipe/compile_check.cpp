// Proves the skeleton header compiles. Forces the template's member bodies to be instantiated.
// Not linked into any executable.
#include "feed_pipe.hpp"

template class katas::FeedPipe<katas::FeedEvent>;
