<?php

declare(strict_types=1);

namespace App\Form;

use App\Entity\Category;
use App\Entity\Tag;
use App\Entity\User;
use App\Entity\Waypoint;
use Doctrine\ORM\EntityRepository;
use Doctrine\ORM\QueryBuilder;
use Symfony\Bridge\Doctrine\Form\Type\EntityType;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\Extension\Core\Type\DateType;
use Symfony\Component\Form\Extension\Core\Type\NumberType;
use Symfony\Component\Form\Extension\Core\Type\TextareaType;
use Symfony\Component\Form\Extension\Core\Type\TextType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;

/**
 * A waypoint on the web; the category and tags to choose from are the owner's.
 */
final class WaypointType extends AbstractType
{
    #[\Override]
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        /** @var User $owner */
        $owner = $options['owner'];
        $ownedBy = static fn (EntityRepository $repository): QueryBuilder => $repository->createQueryBuilder('e')
            ->where('e.owner = :owner')
            ->setParameter('owner', $owner)
            ->orderBy('e.name', \SortDirection::Ascending);

        $builder
            ->add('title', TextType::class, ['label' => 'waypoint.title', 'empty_data' => ''])
            ->add('description', TextareaType::class, [
                'label' => 'waypoint.description',
                'required' => false,
                'empty_data' => '',
                'attr' => ['rows' => 6],
            ])
            ->add('latitude', NumberType::class, ['label' => 'waypoint.latitude', 'scale' => 6, 'html5' => true, 'attr' => ['step' => 'any']])
            ->add('longitude', NumberType::class, ['label' => 'waypoint.longitude', 'scale' => 6, 'html5' => true, 'attr' => ['step' => 'any']])
            ->add('visitedAt', DateType::class, [
                'label' => 'waypoint.visited_at',
                'required' => false,
                'widget' => 'single_text',
                'input' => 'datetime_immutable',
            ])
            ->add('category', EntityType::class, [
                'label' => 'waypoint.category',
                'class' => Category::class,
                'choice_label' => 'name',
                'query_builder' => $ownedBy,
                'required' => false,
                'placeholder' => 'waypoint.no_category',
            ])
            ->add('tags', EntityType::class, [
                'label' => 'waypoint.tags',
                'class' => Tag::class,
                'choice_label' => 'name',
                'query_builder' => $ownedBy,
                'multiple' => true,
                'expanded' => true,
                'required' => false,
                'by_reference' => false,
            ]);
    }

    #[\Override]
    public function configureOptions(OptionsResolver $resolver): void
    {
        $resolver->setDefaults(['data_class' => Waypoint::class]);
        $resolver->setRequired('owner');
        $resolver->setAllowedTypes('owner', User::class);
    }
}
