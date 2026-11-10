<?php

declare(strict_types=1);

namespace App\Entity;

use ApiPlatform\Metadata\ApiProperty;
use ApiPlatform\Metadata\ApiResource;
use ApiPlatform\Metadata\Delete;
use ApiPlatform\Metadata\Get;
use ApiPlatform\Metadata\GetCollection;
use ApiPlatform\Metadata\Patch;
use ApiPlatform\Metadata\Post;
use App\Repository\TrackRepository;
use App\Track\TrackGeometry;
use Doctrine\DBAL\Types\Types;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Serializer\Attribute\Groups;
use Symfony\Component\Validator\Constraints as Assert;
use Symfony\Component\Validator\Context\ExecutionContextInterface;

/**
 * A recorded track: the points the app recorded, each [latitude, longitude, time] with the time
 * in seconds since 1970 (UTC). The server derives the start, the end and the distance.
 */
#[ORM\Entity(repositoryClass: TrackRepository::class)]
#[ORM\HasLifecycleCallbacks]
#[ApiResource(
    description: "The user's recorded tracks. points are [latitude, longitude, time] (WGS 84 degrees, Unix seconds), at least 2 and at most 50000, in the order recorded. startedAt, endedAt and distance (meters) are derived from them. Only the name can be changed.",
    operations: [
        new GetCollection(),
        new Post(validationContext: ['groups' => ['Default', self::CREATE]]),
        new Get(security: "is_granted('OWNER', object)"),
        new Patch(denormalizationContext: ['groups' => [self::RENAME]], security: "is_granted('OWNER', object)"),
        new Delete(security: "is_granted('OWNER', object)"),
    ],
    normalizationContext: ['groups' => [self::READ]],
    denormalizationContext: ['groups' => [self::WRITE]],
    order: ['startedAt' => 'DESC', 'id' => 'DESC'],
    paginationEnabled: false,
    security: "is_granted('ROLE_USER')",
)]
class Track implements OwnedInterface
{
    use OwnedTrait;

    public const string READ = 'track:read';

    public const string WRITE = 'track:write';

    public const string RENAME = 'track:rename';

    public const string CREATE = 'track:create';

    public const int MAX_POINTS = 50_000;

    #[ORM\Id, ORM\GeneratedValue, ORM\Column]
    #[Groups([self::READ])]
    private ?int $id = null;

    #[ORM\Column(length: 255)]
    #[Groups([self::READ, self::WRITE, self::RENAME])]
    #[Assert\NotBlank(normalizer: 'trim')]
    #[Assert\Length(max: 255)]
    private string $name = '';

    /**
     * @var list<array{float, float, int}>
     */
    #[ORM\Column(type: Types::JSON)]
    #[Groups([self::READ, self::WRITE])]
    private array $points = [];

    /**
     * The points as submitted, for the validation; not stored.
     *
     * @var array<mixed>|null
     */
    private ?array $submittedPoints = null;

    #[ORM\Column(type: Types::DATETIMETZ_IMMUTABLE)]
    #[Groups([self::READ])]
    private \DateTimeImmutable $startedAt;

    #[ORM\Column(type: Types::DATETIMETZ_IMMUTABLE)]
    #[Groups([self::READ])]
    private \DateTimeImmutable $endedAt;

    /** Meters along the points. */
    #[ORM\Column]
    #[Groups([self::READ])]
    private float $distance = 0;

    #[ORM\Column(type: Types::DATETIMETZ_IMMUTABLE)]
    #[Groups([self::READ])]
    private \DateTimeImmutable $createdAt;

    public function __construct()
    {
        $this->createdAt = new \DateTimeImmutable();
        $this->startedAt = $this->createdAt;
        $this->endedAt = $this->createdAt;
    }

    #[Assert\Callback(groups: [self::CREATE])]
    public function validatePoints(ExecutionContextInterface $context): void
    {
        $error = TrackGeometry::validate($this->submittedPoints ?? $this->points, self::MAX_POINTS);

        if (null !== $error) {
            $context->buildViolation($error)->atPath('points')->addViolation();
        }
    }

    /** Start, end and distance from the points. */
    #[ORM\PrePersist]
    public function measure(): void
    {
        if ([] === $this->points) {
            return;
        }

        $this->startedAt = new \DateTimeImmutable('@'.$this->points[0][2]);
        $this->endedAt = new \DateTimeImmutable('@'.$this->points[\count($this->points) - 1][2]);
        $this->distance = \round(TrackGeometry::distance($this->points), 1);
    }

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getName(): string
    {
        return $this->name;
    }

    public function setName(string $name): static
    {
        $this->name = \trim($name);

        return $this;
    }

    /**
     * @return list<array{float, float, int}>
     */
    public function getPoints(): array
    {
        return $this->points;
    }

    /**
     * @param array<mixed> $points
     */
    public function setPoints(array $points): static
    {
        // Validated as submitted in validatePoints(); normalized for storing
        $this->submittedPoints = $points;
        $this->points = TrackGeometry::normalize($points);

        return $this;
    }

    /** The number of points. */
    #[Groups([self::READ])]
    #[ApiProperty(writable: false)]
    public function getPointCount(): int
    {
        return \count($this->points);
    }

    public function getStartedAt(): \DateTimeImmutable
    {
        return $this->startedAt;
    }

    public function getEndedAt(): \DateTimeImmutable
    {
        return $this->endedAt;
    }

    public function getDistance(): float
    {
        return $this->distance;
    }

    public function getCreatedAt(): \DateTimeImmutable
    {
        return $this->createdAt;
    }
}
