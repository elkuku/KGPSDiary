<?php

declare(strict_types=1);

namespace App\Form;

use App\Entity\Category;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\Extension\Core\Type\ColorType;
use Symfony\Component\Form\Extension\Core\Type\TextType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;

final class CategoryType extends AbstractType
{
    #[\Override]
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        $builder
            ->add('name', TextType::class, ['label' => 'category.name', 'empty_data' => ''])
            ->add('color', ColorType::class, ['label' => 'category.color', 'empty_data' => Category::DEFAULT_COLOR])
            ->add('icon', TextType::class, [
                'label' => 'category.icon',
                'required' => false,
                'help' => 'category.icon_help',
            ]);
    }

    #[\Override]
    public function configureOptions(OptionsResolver $resolver): void
    {
        $resolver->setDefaults(['data_class' => Category::class]);
    }
}
