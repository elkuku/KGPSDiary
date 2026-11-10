<?php

declare(strict_types=1);

namespace App\Controller\Tag;

use App\Controller\BaseController;
use App\Entity\Tag;
use App\Form\TagType;
use App\Repository\TagRepository;
use App\Repository\WaypointRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

/**
 * The user's tags, and a form for a new one.
 */
#[Route('/tags', name: 'app_tag_index', methods: ['GET', 'POST'])]
#[IsGranted('ROLE_USER')]
final class Index extends BaseController
{
    public function __invoke(
        Request $request,
        TagRepository $tagRepository,
        WaypointRepository $waypointRepository,
        EntityManagerInterface $entityManager,
    ): Response {
        $tag = new Tag()->setOwner($this->getUser());
        $form = $this->createForm(TagType::class, $tag);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $entityManager->persist($tag);
            $entityManager->flush();

            $this->addFlash('success', 'tag.flash.created');

            return $this->redirectToRoute('app_tag_index', [], Response::HTTP_SEE_OTHER);
        }

        return $this->render('tag/index.html.twig', [
            'tags' => $tagRepository->findByOwner($this->getUser()),
            'counts' => $waypointRepository->countByTag($this->getUser()),
            'form' => $form,
        ]);
    }
}
