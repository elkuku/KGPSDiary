<?php

declare(strict_types=1);

namespace App\Controller\Tag;

use App\Controller\BaseController;
use App\Entity\Tag;
use App\Form\TagType;
use App\Security\Voter\OwnerVoter;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

#[Route('/tags/{id}/edit', name: 'app_tag_edit', requirements: ['id' => '\d+'], methods: ['GET', 'POST'])]
#[IsGranted('ROLE_USER')]
#[IsGranted(OwnerVoter::OWNER, subject: 'tag', statusCode: 404)]
final class Update extends BaseController
{
    public function __invoke(Request $request, Tag $tag, EntityManagerInterface $entityManager): Response
    {
        $form = $this->createForm(TagType::class, $tag);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $entityManager->flush();

            $this->addFlash('success', 'tag.flash.updated');

            return $this->redirectToRoute('app_tag_index', [], Response::HTTP_SEE_OTHER);
        }

        return $this->render('tag/edit.html.twig', ['tag' => $tag, 'form' => $form]);
    }
}
