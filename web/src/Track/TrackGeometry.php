<?php

declare(strict_types=1);

namespace App\Track;

/**
 * Track points: [latitude, longitude, time] with WGS 84 degrees and Unix seconds.
 */
final class TrackGeometry
{
    private const float EARTH_RADIUS = 6_371_008.8;

    /**
     * Why [points] are no track, or null when they are one.
     *
     * @param array<mixed> $points
     */
    public static function validate(array $points, int $max): ?string
    {
        if (!\array_is_list($points) || \count($points) < 2) {
            return 'A track needs at least 2 points.';
        }

        if (\count($points) > $max) {
            return \sprintf('A track has at most %d points.', $max);
        }

        return self::validatePoints($points);
    }

    /**
     * @param list<mixed> $points
     */
    private static function validatePoints(array $points): ?string
    {
        $previous = 0;

        foreach ($points as $point) {
            if (!self::isPoint($point)) {
                return 'Each point is [latitude, longitude, time] with the time in Unix seconds.';
            }

            if ($point[2] < $previous) {
                return 'The points must be in the order they were recorded.';
            }

            $previous = $point[2];
        }

        return null;
    }

    /**
     * @param array<mixed> $points
     *
     * @return list<array{float, float, int}> the valid points, with 6 decimals (about 10 cm)
     */
    public static function normalize(array $points): array
    {
        $normalized = [];

        foreach ($points as $point) {
            if (self::isPoint($point)) {
                $normalized[] = [\round((float) $point[0], 6), \round((float) $point[1], 6), (int) $point[2]];
            }
        }

        return $normalized;
    }

    /**
     * Meters along the points (great circle distances).
     *
     * @param list<array{float, float, int}> $points
     */
    public static function distance(array $points): float
    {
        $distance = 0.0;

        for ($i = 1, $count = \count($points); $i < $count; ++$i) {
            $distance += self::between($points[$i - 1][0], $points[$i - 1][1], $points[$i][0], $points[$i][1]);
        }

        return $distance;
    }

    public static function between(float $latitude1, float $longitude1, float $latitude2, float $longitude2): float
    {
        $phi1 = \deg2rad($latitude1);
        $phi2 = \deg2rad($latitude2);
        $a = \sin(($phi2 - $phi1) / 2) ** 2 + \cos($phi1) * \cos($phi2) * \sin(\deg2rad($longitude2 - $longitude1) / 2) ** 2;

        return 2 * self::EARTH_RADIUS * \asin(\min(1, \sqrt($a)));
    }

    /**
     * @phpstan-assert-if-true array{int|float, int|float, int} $point
     */
    private static function isPoint(mixed $point): bool
    {
        return \is_array($point)
            && 3 === \count($point)
            && \array_is_list($point)
            && (\is_int($point[0]) || \is_float($point[0])) && \abs($point[0]) <= 90
            && (\is_int($point[1]) || \is_float($point[1])) && \abs($point[1]) <= 180
            && \is_int($point[2]) && $point[2] > 0;
    }
}
