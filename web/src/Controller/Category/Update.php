<?php

declare(strict_types=1);

namespace App\Controller\Category;

use App\Controller\BaseController;
use App\Entity\Category;
use App\Form\CategoryType;
use App\Security\Voter\OwnerVoter;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

#[Route('/categories/{id}/edit', name: 'app_category_edit', requirements: ['id' => '\d+'], methods: ['GET', 'POST'])]
#[IsGranted('ROLE_USER')]
#[IsGranted(OwnerVoter::OWNER, subject: 'category', statusCode: 404)]
final class Update extends BaseController
{
    public function __invoke(Request $request, Category $category, EntityManagerInterface $entityManager): Response
    {
        $form = $this->createForm(CategoryType::class, $category);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $entityManager->flush();

            $this->addFlash('success', 'category.flash.updated');

            return $this->redirectToRoute('app_category_index', [], Response::HTTP_SEE_OTHER);
        }

        return $this->render('category/edit.html.twig', ['category' => $category, 'form' => $form]);
    }
}
