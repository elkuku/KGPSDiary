<?php

declare(strict_types=1);

namespace App\Controller\Waypoint;

use App\Controller\BaseController;
use App\Repository\WaypointRepository;
use App\Waypoint\WaypointSearch;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

/**
 * The user's waypoints, newest visit first; `?q=` searches them.
 */
#[Route('/waypoints', name: 'app_waypoint_index', methods: ['GET'])]
#[IsGranted('ROLE_USER')]
final class Index extends BaseController
{
    public function __invoke(Request $request, WaypointRepository $waypointRepository): Response
    {
        $query = \trim($request->query->getString('q'));
        $all = $waypointRepository->findByOwnerNewestFirst($this->getUser());

        return $this->render('waypoint/index.html.twig', [
            'query' => $query,
            'all' => \count($all),
            'waypoints' => WaypointSearch::filter($all, $query),
        ]);
    }
}
