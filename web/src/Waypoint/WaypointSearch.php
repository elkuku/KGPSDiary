<?php

declare(strict_types=1);

namespace App\Waypoint;

use App\Entity\Tag;
use App\Entity\Waypoint;

use function Symfony\Component\String\u;

/**
 * Finds waypoints that contain every word of a query in their title, description, category or
 * tags, ignoring case and accents ("basilica" finds "Basílica"), as the app's search does.
 */
final class WaypointSearch
{
    /**
     * @param iterable<Waypoint> $waypoints
     *
     * @return list<Waypoint>
     */
    public static function filter(iterable $waypoints, string $query): array
    {
        $words = \array_values(\array_filter(\explode(' ', self::searchable($query))));
        $found = [];

        foreach ($waypoints as $waypoint) {
            $text = self::searchable(\implode(' ', [
                $waypoint->getTitle(),
                $waypoint->getDescription(),
                $waypoint->getCategory()?->getName() ?? '',
                ...$waypoint->getTags()->map(static fn (Tag $tag): string => $tag->getName())->getValues(),
            ]));

            if (\array_all($words, static fn (string $word): bool => \str_contains($text, $word))) {
                $found[] = $waypoint;
            }
        }

        return $found;
    }

    private static function searchable(string $text): string
    {
        return u($text)->ascii()->lower()->collapseWhitespace()->toString();
    }
}
