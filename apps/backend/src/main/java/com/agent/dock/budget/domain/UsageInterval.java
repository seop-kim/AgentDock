package com.agent.dock.budget.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * 사용량 버킷 단위. UTC 기준으로 시각을 아래로 정렬(truncate)하고 다음 경계를 계산한다.
 * 주는 월요일 시작이다(LocalDate.getDayOfWeek: 월=1).
 */
public enum UsageInterval {
    DAY {
        @Override public Instant start(Instant time) {
            return time.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        }
        @Override public Instant next(Instant start) {
            return start.atZone(ZoneOffset.UTC).plusDays(1).toInstant();
        }
    },
    WEEK {
        @Override public Instant start(Instant time) {
            LocalDate date = time.atZone(ZoneOffset.UTC).toLocalDate();
            return date.minusDays((date.getDayOfWeek().getValue() + 6) % 7)
                    .atStartOfDay(ZoneOffset.UTC).toInstant();
        }
        @Override public Instant next(Instant start) {
            return start.atZone(ZoneOffset.UTC).plusWeeks(1).toInstant();
        }
    },
    MONTH {
        @Override public Instant start(Instant time) {
            return time.atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1)
                    .atStartOfDay(ZoneOffset.UTC).toInstant();
        }
        @Override public Instant next(Instant start) {
            return start.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
        }
    };

    public abstract Instant start(Instant time);

    public abstract Instant next(Instant start);

    public static UsageInterval of(BudgetPeriod period) {
        return switch (period) {
            case DAILY -> DAY;
            case WEEKLY -> WEEK;
            case MONTHLY -> MONTH;
        };
    }
}