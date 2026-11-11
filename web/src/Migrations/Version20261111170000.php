<?php

declare(strict_types=1);

namespace App\Migrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Categories can only have one of the category symbols now: other icon names, typed freely
 * before and never shown, are cleared.
 */
final class Version20261111170000 extends AbstractMigration
{
    #[\Override]
    public function getDescription(): string
    {
        return 'Clear category icons that are not one of the category symbols';
    }

    public function up(Schema $schema): void
    {
        $this->addSql("UPDATE category SET icon = NULL WHERE icon IS NOT NULL AND icon NOT IN ('restaurant', 'local_cafe', 'local_bar', 'hotel', 'home', 'shopping_cart', 'local_hospital', 'directions_car', 'local_parking', 'directions_walk', 'terrain', 'park', 'beach_access', 'water_drop', 'museum', 'account_balance', 'photo_camera', 'visibility', 'star', 'favorite', 'flag', 'place')");
    }

    #[\Override]
    public function down(Schema $schema): void
    {
        // The cleared names are gone
    }
}
