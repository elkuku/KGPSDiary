<?php

declare(strict_types=1);

namespace App\Entity;

/**
 * A record that belongs to one user. Users only see and change their own records: see
 * OwnerExtension (queries), OwnerProcessor (new records) and OwnerVoter (access).
 */
interface OwnedInterface
{
    public function getOwner(): ?User;

    public function setOwner(User $owner): static;
}
