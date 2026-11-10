<?php

declare(strict_types=1);

namespace App\Controller\Track;

use App\Controller\BaseController;
use App\Repository\TrackRepository;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

/**
 * The user's recorded tracks, newest first. Tracks are recorded with the app.
 */
#[Route('/tracks', name: 'app_track_index', methods: ['GET'])]
#[IsGranted('ROLE_USER')]
final class Index extends BaseController
{
    public function __invoke(TrackRepository $trackRepository): Response
    {
        return $this->render('track/index.html.twig', [
            'tracks' => $trackRepository->findBy(['owner' => $this->getUser()], ['startedAt' => 'DESC', 'id' => 'DESC']),
        ]);
    }
}
