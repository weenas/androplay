/*
 * DNS-SD backend for UxPlay's lib/dnssd.c on Android.
 *
 * UxPlay splits DNS-SD into a generic front end (lib/dnssd.c: name, hardware address,
 * public key, feature bits) and a backend that builds the TXT records and publishes them
 * (lib/dns_sd/ or lib/mdnsd/). On Android the services are published from Kotlin with
 * NsdManager, so this backend only builds the TXT records. The same bytes are served
 * in GET /info and handed to Kotlin, so what senders discover always matches the
 * handshake (in particular the per-device "pk").
 *
 * Record contents mirror lib/dns_sd/dns_sd.c (pin/password modes are not used).
 */
#include "dnssd.h"
#include "dnssdint.h"
#include "global.h"
#include "utils.h"

#include <android/log.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define TXT_CAPACITY 1024

typedef struct {
    char raop_txt[TXT_CAPACITY];
    int raop_txt_len;
    char airplay_txt[TXT_CAPACITY];
    int airplay_txt_len;
} dnssd_private_t;

/* Appends one "key=value" entry in DNS TXT wire format (length-prefixed). */
static int txt_append(char *buffer, int *length, const char *key, const char *value) {
    size_t key_len = strlen(key);
    size_t value_len = strlen(value);
    size_t entry_len = key_len + 1 + value_len;
    if (entry_len > 255 || (size_t) *length + 1 + entry_len > TXT_CAPACITY) return -1;
    buffer[(*length)++] = (char) entry_len;
    memcpy(buffer + *length, key, key_len);
    *length += (int) key_len;
    buffer[(*length)++] = '=';
    memcpy(buffer + *length, value, value_len);
    *length += (int) value_len;
    return 0;
}

#define TXT(buf, len, key, value) do { if (txt_append(buf, len, key, value) < 0) return -1; } while (0)

void *dnssd_private_init(dnssd_t *dnssd_public, int *error) {
    (void) dnssd_public;
    dnssd_private_t *dnssd = calloc(1, sizeof(dnssd_private_t));
    if (!dnssd && error) *error = DNSSD_ERROR_OUTOFMEM;
    return dnssd;
}

void dnssd_private_destroy(void *dnssd_private) {
    free(dnssd_private);
}

int dnssd_register_raop(dnssd_t *dnssd_public, unsigned short port) {
    (void) port;
    dnssd_private_t *dnssd = (dnssd_private_t *) dnssd_public->dnssd_private;
    char features[22];
    snprintf(features, sizeof(features), "0x%X,0x%X", dnssd_public->features1, dnssd_public->features2);

    char *buf = dnssd->raop_txt;
    int *len = &dnssd->raop_txt_len;
    *len = 0;
    TXT(buf, len, "ch", RAOP_CH);
    TXT(buf, len, "cn", RAOP_CN);
    TXT(buf, len, "da", RAOP_DA);
    TXT(buf, len, "et", RAOP_ET);
    TXT(buf, len, "vv", RAOP_VV);
    TXT(buf, len, "ft", features);
    TXT(buf, len, "am", GLOBAL_MODEL);
    TXT(buf, len, "md", RAOP_MD);
    TXT(buf, len, "rhd", RAOP_RHD);
    TXT(buf, len, "pw", "false");
    TXT(buf, len, "sf", RAOP_SF);
    TXT(buf, len, "sr", RAOP_SR);
    TXT(buf, len, "ss", RAOP_SS);
    TXT(buf, len, "sv", RAOP_SV);
    TXT(buf, len, "tp", RAOP_TP);
    TXT(buf, len, "txtvers", RAOP_TXTVERS);
    TXT(buf, len, "vs", RAOP_VS);
    TXT(buf, len, "vn", RAOP_VN);
    TXT(buf, len, "pk", dnssd_public->pk);
    return 0;
}

int dnssd_register_airplay(dnssd_t *dnssd_public, unsigned short port) {
    (void) port;
    dnssd_private_t *dnssd = (dnssd_private_t *) dnssd_public->dnssd_private;
    char features[22];
    snprintf(features, sizeof(features), "0x%X,0x%X", dnssd_public->features1, dnssd_public->features2);
    char device_id[3 * MAX_HWADDR_LEN];
    if (utils_hwaddr_airplay(device_id, sizeof(device_id), dnssd_public->hw_addr, dnssd_public->hw_addr_len) < 0) {
        return -1;
    }

    char *buf = dnssd->airplay_txt;
    int *len = &dnssd->airplay_txt_len;
    *len = 0;
    TXT(buf, len, "deviceid", device_id);
    TXT(buf, len, "features", features);
    TXT(buf, len, "pw", "false");
    TXT(buf, len, "flags", "0x4");
    TXT(buf, len, "model", GLOBAL_MODEL);
    TXT(buf, len, "pk", dnssd_public->pk);
    TXT(buf, len, "pi", AIRPLAY_PI);
    TXT(buf, len, "srcvers", AIRPLAY_SRCVERS);
    TXT(buf, len, "vv", AIRPLAY_VV);
    return 0;
}

void dnssd_unregister_raop(dnssd_t *dnssd_public) {
    ((dnssd_private_t *) dnssd_public->dnssd_private)->raop_txt_len = 0;
}

void dnssd_unregister_airplay(dnssd_t *dnssd_public) {
    ((dnssd_private_t *) dnssd_public->dnssd_private)->airplay_txt_len = 0;
}

const char *dnssd_get_raop_txt(dnssd_t *dnssd_public, int *length) {
    dnssd_private_t *dnssd = (dnssd_private_t *) dnssd_public->dnssd_private;
    *length = dnssd->raop_txt_len;
    return dnssd->raop_txt;
}

const char *dnssd_get_airplay_txt(dnssd_t *dnssd_public, int *length) {
    dnssd_private_t *dnssd = (dnssd_private_t *) dnssd_public->dnssd_private;
    *length = dnssd->airplay_txt_len;
    return dnssd->airplay_txt;
}

void dnssd_error_text(int *error, const char *appname) {
    __android_log_print(ANDROID_LOG_ERROR, appname ? appname : "dnssd",
                        "DNS-SD TXT record error %d", error ? *error : 0);
}
