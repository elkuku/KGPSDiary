<?php

declare(strict_types=1);

namespace App\Controller\Waypoint;

use App\Controller\BaseController;
use App\Entity\Tag;
use App\Repository\WaypointRepository;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

/**
 * The user's waypoints as GeoJSON for the web map.
 */
#[Route('/map/waypoints.geojson', name: 'app_waypoint_features', methods: ['GET'])]
#[IsGranted('ROLE_USER')]
final class Features extends BaseController
{
    public function __invoke(WaypointRepository $waypointRepository): JsonResponse
    {
        $features = [];

        foreach ($waypointRepository->findBy(['owner' => $this->getUser()]) as $waypoint) {
            $category = $waypoint->getCategory();
            $features[] = [
                'type' => 'Feature',
                'geometry' => ['type' => 'Point', 'coordinates' => [$waypoint->getLongitude(), $waypoint->getLatitude()]],
                'properties' => [
                    'id' => $waypoint->getId(),
                    'title' => $waypoint->getTitle(),
                    'visitedAt' => $waypoint->getVisitedAt()?->format('Y-m-d'),
                    'category' => $category?->getName(),
                    'categoryId' => $category?->getId(),
                    'tagIds' => $waypoint->getTags()->map(static fn (Tag $tag): ?int => $tag->getId())->getValues(),
                    'color' => $category?->getColor() ?? '#d84315',
                    // The marker's symbol (see category_symbol_paths()), '' for none
                    'icon' => $category?->getIcon() ?? '',
                    'url' => $this->generateUrl('app_waypoint_show', ['id' => $waypoint->getId()]),
                ],
            ];
        }

        return new JsonResponse(['type' => 'FeatureCollection', 'features' => $features], headers: ['Content-Type' => 'application/geo+json']);
    }
}
