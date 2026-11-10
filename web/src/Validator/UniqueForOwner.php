<?php

declare(strict_types=1);

namespace App\Validator;

use Symfony\Component\Validator\Attribute\HasNamedArguments;
use Symfony\Component\Validator\Constraint;

/**
 * No other record of the same owner has this value in [field]. New records get their owner only
 * when they are saved (OwnerProcessor), so until then the signed-in user counts as the owner.
 */
#[\Attribute(\Attribute::TARGET_CLASS)]
final class UniqueForOwner extends Constraint
{
    public string $message = 'You already have one with this {{ field }}.';

    /**
     * @param array<string>|null $groups
     */
    #[HasNamedArguments]
    public function __construct(public string $field, ?array $groups = null, mixed $payload = null)
    {
        parent::__construct(null, $groups, $payload);
    }

    #[\Override]
    public function getTargets(): string
    {
        return self::CLASS_CONSTRAINT;
    }
}
