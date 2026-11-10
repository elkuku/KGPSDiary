<?php

declare(strict_types=1);

namespace App\State;

use ApiPlatform\Metadata\Operation;
use ApiPlatform\State\ProcessorInterface;
use App\Entity\OwnedInterface;
use App\Entity\User;
use Symfony\Bundle\SecurityBundle\Security;
use Symfony\Component\DependencyInjection\Attribute\AsDecorator;
use Symfony\Component\DependencyInjection\Attribute\AutowireDecorated;
use Symfony\Component\Security\Core\Exception\AccessDeniedException;

/**
 * New {@see OwnedInterface} records belong to the signed-in user.
 *
 * @implements ProcessorInterface<mixed, mixed>
 */
#[AsDecorator('api_platform.doctrine.orm.state.persist_processor')]
final readonly class OwnerProcessor implements ProcessorInterface
{
    /**
     * @param ProcessorInterface<mixed, mixed> $inner
     */
    public function __construct(
        #[AutowireDecorated] private ProcessorInterface $inner,
        private Security $security,
    ) {
    }

    #[\Override]
    public function process(mixed $data, Operation $operation, array $uriVariables = [], array $context = []): mixed
    {
        if ($data instanceof OwnedInterface && !$data->getOwner() instanceof User) {
            $user = $this->security->getUser();

            if (!$user instanceof User) {
                throw new AccessDeniedException();
            }

            $data->setOwner($user);
        }

        return $this->inner->process($data, $operation, $uriVariables, $context);
    }
}
