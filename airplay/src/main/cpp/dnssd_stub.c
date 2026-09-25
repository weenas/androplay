#include "dnssd.h"
#include "dnssdint.h"
#include "global.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

struct dnssd_s {
    char *name;
    int name_len;
    char hw_addr[6];
    int hw_addr_len;
    char *airplay_txt;
    int airplay_txt_len;
};

static int append_txt(char *buffer, int capacity, int offset, const char *key,
                      const char *value) {
    int entry_len = (int) strlen(key) + 1 + (int) strlen(value);
    if (entry_len > 255 || offset + entry_len + 1 > capacity) return -1;
    buffer[offset++] = (char) entry_len;
    memcpy(buffer + offset, key, strlen(key));
    offset += (int) strlen(key);
    buffer[offset++] = '=';
    memcpy(buffer + offset, value, strlen(value));
    return offset + (int) strlen(value);
}

static int build_airplay_txt(dnssd_t *dnssd) {
    char device_id[18];
    snprintf(device_id, sizeof(device_id), "%02X:%02X:%02X:%02X:%02X:%02X",
             (unsigned char) dnssd->hw_addr[0], (unsigned char) dnssd->hw_addr[1],
             (unsigned char) dnssd->hw_addr[2], (unsigned char) dnssd->hw_addr[3],
             (unsigned char) dnssd->hw_addr[4], (unsigned char) dnssd->hw_addr[5]);

    const int capacity = 512;
    dnssd->airplay_txt = (char *) calloc(1, capacity);
    if (!dnssd->airplay_txt) return -1;
    int offset = 0;
#define APPEND_TXT(key, value) do { \
    offset = append_txt(dnssd->airplay_txt, capacity, offset, key, value); \
    if (offset < 0) return -1; \
} while (0)
    APPEND_TXT("deviceid", device_id);
    APPEND_TXT("features", AIRPLAY_FEATURES);
    APPEND_TXT("flags", AIRPLAY_FLAGS);
    APPEND_TXT("model", GLOBAL_MODEL);
    APPEND_TXT("pk", AIRPLAY_PK);
    APPEND_TXT("pi", AIRPLAY_PI);
    APPEND_TXT("srcvers", AIRPLAY_SRCVERS);
    APPEND_TXT("vv", AIRPLAY_VV);
#undef APPEND_TXT
    dnssd->airplay_txt_len = offset;
    return 0;
}

dnssd_t *dnssd_init(const char *name, int name_len, const char *hw_addr,
                    int hw_addr_len, int *error) {
    if (hw_addr_len != 6) {
        if (error) *error = DNSSD_ERROR_HWADDRLEN;
        return NULL;
    }
    dnssd_t *dnssd = (dnssd_t *) calloc(1, sizeof(dnssd_t));
    if (!dnssd) {
        if (error) *error = DNSSD_ERROR_OUTOFMEM;
        return NULL;
    }
    dnssd->name = (char *) malloc((size_t) name_len + 1);
    if (!dnssd->name) {
        free(dnssd);
        if (error) *error = DNSSD_ERROR_OUTOFMEM;
        return NULL;
    }
    memcpy(dnssd->name, name, (size_t) name_len);
    dnssd->name[name_len] = '\0';
    dnssd->name_len = name_len;
    memcpy(dnssd->hw_addr, hw_addr, 6);
    dnssd->hw_addr_len = 6;
    if (build_airplay_txt(dnssd) < 0) {
        free(dnssd->airplay_txt);
        free(dnssd->name);
        free(dnssd);
        if (error) *error = DNSSD_ERROR_OUTOFMEM;
        return NULL;
    }
    if (error) *error = DNSSD_ERROR_NOERROR;
    return dnssd;
}

int dnssd_register_raop(dnssd_t *dnssd, unsigned short port) {
    (void) dnssd;
    (void) port;
    return 0;
}

int dnssd_register_airplay(dnssd_t *dnssd, unsigned short port) {
    (void) dnssd;
    (void) port;
    return 0;
}

void dnssd_unregister_raop(dnssd_t *dnssd) { (void) dnssd; }
void dnssd_unregister_airplay(dnssd_t *dnssd) { (void) dnssd; }

const char *dnssd_get_airplay_txt(dnssd_t *dnssd, int *length) {
    if (length) *length = dnssd->airplay_txt_len;
    return dnssd->airplay_txt;
}

const char *dnssd_get_name(dnssd_t *dnssd, int *length) {
    if (length) *length = dnssd->name_len;
    return dnssd->name;
}

const char *dnssd_get_hw_addr(dnssd_t *dnssd, int *length) {
    if (length) *length = dnssd->hw_addr_len;
    return dnssd->hw_addr;
}

void dnssd_destroy(dnssd_t *dnssd) {
    if (!dnssd) return;
    free(dnssd->airplay_txt);
    free(dnssd->name);
    free(dnssd);
}
