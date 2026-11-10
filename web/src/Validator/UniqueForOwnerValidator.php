<?php

declare(strict_types=1);

namespace App\Validator;

use App\Entity\OwnedInterface;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\SecurityBundle\Security;
use Symfony\Component\PropertyAccess\PropertyAccess;
use Symfony\Component\Validator\Constraint;
use Symfony\Component\Validator\ConstraintValidator;
use Symfony\Component\Validator\Exception\UnexpectedTypeException;
use Symfony\Component\Validator\Exception\UnexpectedValueException;

final class UniqueForOwnerValidator extends ConstraintValidator
{
    public function __construct(
        private readonly EntityManagerInterface $entityManager,
        private readonly Security $security,
    ) {
    }

    #[\Override]
    public function validate(mixed $value, Constraint $constraint): void
    {
        if (!$constraint instanceof UniqueForOwner) {
            throw new UnexpectedTypeException($constraint, UniqueForOwner::class);
        }

        if (!$value instanceof OwnedInterface) {
            throw new UnexpectedValueException($value, OwnedInterface::class);
        }

        $owner = $value->getOwner() ?? $this->security->getUser();
        $fieldValue = PropertyAccess::createPropertyAccessor()->getValue($value, $constraint->field);

        if (!$owner instanceof \Symfony\Component\Security\Core\User\UserInterface || null === $fieldValue || '' === $fieldValue) {
            return;
        }

        $existing = $this->entityManager->getRepository($value::class)
            ->findOneBy(['owner' => $owner, $constraint->field => $fieldValue]);

        if (null !== $existing && $existing !== $value) {
            $this->context->buildViolation($constraint->message)
                ->setParameter('{{ field }}', $constraint->field)
                ->atPath($constraint->field)
                ->addViolation();
        }
    }
}
