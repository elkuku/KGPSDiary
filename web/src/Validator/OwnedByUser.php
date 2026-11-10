<?php

declare(strict_types=1);

namespace App\Validator;

use Symfony\Component\Validator\Attribute\HasNamedArguments;
use Symfony\Component\Validator\Constraint;

/**
 * The value (an {@see \App\Entity\OwnedInterface}, or null) belongs to the signed-in user.
 */
#[\Attribute(\Attribute::TARGET_PROPERTY | \Attribute::TARGET_METHOD)]
final class OwnedByUser extends Constraint
{
    public string $message = 'This item does not exist.';

    /**
     * @param array<string>|null $groups
     */
    #[HasNamedArguments]
    public function __construct(?array $groups = null, mixed $payload = null)
    {
        parent::__construct(null, $groups, $payload);
    }
}
