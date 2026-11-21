/*
 * Copyright 2026 KindGuyJ
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.notificators;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import org.traccar.config.Config;
import org.traccar.helper.WebHelper;
import org.traccar.model.Device;
import org.traccar.model.Event;
import org.traccar.model.Position;
import org.traccar.model.User;
import org.traccar.notification.NotificationFormatter;
import org.traccar.notification.NotificationMessage;
import org.traccar.session.cache.CacheManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Hands each alert to rastreo-api, which sends it as Web Push to the user's subscribed browsers.
 * Traccar has already matched the alert against the user's notification rules (type, alarms,
 * zones, calendar, linked users), so the service only looks up where to deliver it.
 * Config: notificator.webpush.url (e.g. http://127.0.0.1:8090/internal/alerts) and
 * notificator.webpush.secret, sent as X-Alerts-Secret. Kept in one file, with its keys read by
 * name, so the fork's change stays this class plus one line in NotificatorManager.
 */
@Singleton
public class NotificatorWebpush extends Notificator {

    private final Client client;
    private final CacheManager cacheManager;
    private final String url;
    private final String secret;

    @Inject
    public NotificatorWebpush(
            Config config, NotificationFormatter notificationFormatter, Client client, CacheManager cacheManager) {
        super(notificationFormatter);
        this.client = client;
        this.cacheManager = cacheManager;
        url = config.getString("notificator.webpush.url");
        secret = config.getString("notificator.webpush.secret");
    }

    @Override
    public CompletableFuture<Void> sendAsync(
            User user, NotificationMessage message, Event event, Position position) {
        if (url == null || secret == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "notificator.webpush.url and notificator.webpush.secret are required"));
        }
        Device device = cacheManager.getObject(Device.class, event.getDeviceId());

        Map<String, Object> alert = new HashMap<>();
        alert.put("userId", user.getId());
        alert.put("eventId", event.getId());
        alert.put("eventType", event.getType());
        alert.put("eventTime", event.getEventTime().getTime());
        alert.put("deviceId", event.getDeviceId());
        alert.put("deviceName", device != null ? device.getName() : null);
        alert.put("alarm", event.getString(Position.KEY_ALARM));
        alert.put("subject", message.subject());
        alert.put("digest", message.digest());
        if (position != null) {
            alert.put("latitude", position.getLatitude());
            alert.put("longitude", position.getLongitude());
        }

        var request = client.target(url).request().header("X-Alerts-Secret", secret);
        return WebHelper.post(request, Entity.json(alert), response -> {
            if (response.getStatus() / 100 != 2) {
                throw new IllegalStateException("rastreo-api answered " + response.getStatus());
            }
        });
    }

}
