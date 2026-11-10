<?php

declare(strict_types=1);

namespace App\Controller\Track;

use App\Controller\BaseController;
use App\Entity\Track;
use App\Repository\TrackRepository;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

/**
 * The user's tracks as GeoJSON lines for the web map.
 */
#[Route('/map/tracks.geojson', name: 'app_track_features', methods: ['GET'])]
#[IsGranted('ROLE_USER')]
final class Features extends BaseController
{
    public function __invoke(TrackRepository $trackRepository): JsonResponse
    {
        $features = \array_map(
            static fn (Track $track): array => [
                'type' => 'Feature',
                'geometry' => [
                    'type' => 'LineString',
                    // GeoJSON is longitude, latitude
                    'coordinates' => \array_map(static fn (array $point): array => [$point[1], $point[0]], $track->getPoints()),
                ],
                'properties' => [
                    'id' => $track->getId(),
                    'name' => $track->getName(),
                    'startedAt' => $track->getStartedAt()->format(\DATE_ATOM),
                    'distance' => $track->getDistance(),
                ],
            ],
            $trackRepository->findBy(['owner' => $this->getUser()]),
        );

        return new JsonResponse(['type' => 'FeatureCollection', 'features' => $features], headers: ['Content-Type' => 'application/geo+json']);
    }
}
