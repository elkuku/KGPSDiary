<?php

declare(strict_types=1);

namespace App\Controller\Waypoint;

use App\Controller\BaseController;
use App\Repository\CategoryRepository;
use App\Repository\TagRepository;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

/**
 * The map of the user's waypoints; `?latitude=…&longitude=…` centers it on a place.
 */
#[Route('/map', name: 'app_waypoint_map', methods: ['GET'])]
#[IsGranted('ROLE_USER')]
final class Map extends BaseController
{
    public function __invoke(Request $request, CategoryRepository $categoryRepository, TagRepository $tagRepository): Response
    {
        $latitude = $request->query->get('latitude');
        $longitude = $request->query->get('longitude');
        $center = \is_numeric($latitude) && \is_numeric($longitude) ? [(float) $longitude, (float) $latitude] : null;

        return $this->render('waypoint/map.html.twig', [
            'center' => $center,
            'categories' => $categoryRepository->findByOwner($this->getUser()),
            'tags' => $tagRepository->findByOwner($this->getUser()),
        ]);
    }
}
