/*
 * UxPlay's raop.c, built together with helpers that need its private raop_s (the httpd
 * connection table). CMakeLists.txt compiles this file in place of raop.c, so the
 * submodule stays unmodified.
 */
#include "raop.c"
#include "raop_ext.h"

int raop_ext_drop_orphaned_reverse(raop_t *raop) {
    /*
     * A sender's reverse-HTTP ("PTTH") event channel belongs to its AirPlay video
     * connection. Some senders close the AirPlay connection when they leave but never the
     * reverse one, which then keeps the session open and blocks the next sender's
     * reverse channel ("multiple PTTH connections are forbidden").
     */
    if (httpd_count_connection_type(raop->httpd, CONNECTION_TYPE_AIRPLAY) > 0) return 0;
    if (httpd_count_connection_type(raop->httpd, CONNECTION_TYPE_PTTH) == 0) return 0;
    httpd_remove_connections_by_type(raop->httpd, CONNECTION_TYPE_PTTH);
    return 1;
}
