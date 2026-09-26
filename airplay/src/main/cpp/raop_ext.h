#ifndef CASTBAY_RAOP_EXT_H
#define CASTBAY_RAOP_EXT_H

#include "raop.h"

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Closes reverse-HTTP connections left behind once no AirPlay video connection remains.
 * Returns 1 when there were any (the sender's video session is over), else 0.
 */
int raop_ext_drop_orphaned_reverse(raop_t *raop);

#ifdef __cplusplus
}
#endif

#endif
