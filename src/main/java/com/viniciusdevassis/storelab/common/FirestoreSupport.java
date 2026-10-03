package com.viniciusdevassis.storelab.common;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public final class FirestoreSupport {
    private FirestoreSupport() {}

    public static Map<String, Object> document(Object... pairs) {
        Map<String, Object> result = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            Object value = pairs[i + 1];
            result.put((String) pairs[i], value instanceof Instant instant ? Date.from(instant) : value);
        }
        return result;
    }

    public static Instant instant(DocumentSnapshot snapshot, String field) {
        Timestamp timestamp = snapshot.getTimestamp(field);
        if (timestamp != null) return timestamp.toDate().toInstant();
        Date date = snapshot.getDate(field);
        return date == null ? null : date.toInstant();
    }

    public static long number(DocumentSnapshot snapshot, String field) {
        Long value = snapshot.getLong(field);
        return value == null ? 0 : value;
    }

    public static boolean bool(DocumentSnapshot snapshot, String field) {
        Boolean value = snapshot.getBoolean(field);
        return Boolean.TRUE.equals(value);
    }
}
