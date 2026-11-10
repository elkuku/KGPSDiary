<?php

declare(strict_types=1);

namespace App\Security\Voter;

use App\Entity\OwnedInterface;
use App\Entity\User;
use Symfony\Component\Security\Core\Authentication\Token\TokenInterface;
use Symfony\Component\Security\Core\Authorization\Voter\Vote;
use Symfony\Component\Security\Core\Authorization\Voter\Voter;

/**
 * `is_granted('OWNER', $record)`: the record belongs to the signed-in user. Admins have no access
 * to other users' diaries either.
 *
 * @extends Voter<string, OwnedInterface>
 */
final class OwnerVoter extends Voter
{
    public const string OWNER = 'OWNER';

    #[\Override]
    protected function supports(string $attribute, mixed $subject): bool
    {
        return self::OWNER === $attribute && $subject instanceof OwnedInterface;
    }

    #[\Override]
    protected function voteOnAttribute(string $attribute, mixed $subject, TokenInterface $token, ?Vote $vote = null): bool
    {
        $user = $token->getUser();

        return $user instanceof User && $subject->getOwner() === $user;
    }
}
