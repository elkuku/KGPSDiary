<?php

declare(strict_types=1);

namespace App\Validator;

use App\Entity\OwnedInterface;
use Symfony\Bundle\SecurityBundle\Security;
use Symfony\Component\Validator\Constraint;
use Symfony\Component\Validator\ConstraintValidator;
use Symfony\Component\Validator\Exception\UnexpectedTypeException;
use Symfony\Component\Validator\Exception\UnexpectedValueException;

final class OwnedByUserValidator extends ConstraintValidator
{
    public function __construct(private readonly Security $security)
    {
    }

    #[\Override]
    public function validate(mixed $value, Constraint $constraint): void
    {
        if (!$constraint instanceof OwnedByUser) {
            throw new UnexpectedTypeException($constraint, OwnedByUser::class);
        }

        if (null === $value) {
            return;
        }

        if (!$value instanceof OwnedInterface) {
            throw new UnexpectedValueException($value, OwnedInterface::class);
        }

        if ($value->getOwner() !== $this->security->getUser()) {
            $this->context->buildViolation($constraint->message)->addViolation();
        }
    }
}
