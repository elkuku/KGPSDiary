<?php

declare(strict_types=1);

namespace App\Migrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Initial schema. Squashes the former DoctrineMigrations\Version20231110100332,
 * Version20251012133926 and Version20261103135536.
 */
final class Version20261103154042 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Initial schema';
    }

    public function up(Schema $schema): void
    {
        $this->addSql('CREATE SEQUENCE system_user_id_seq INCREMENT BY 1 MINVALUE 1 START 1');
        $this->addSql('CREATE TABLE system_user (id INT NOT NULL, identifier VARCHAR(255) NOT NULL, role VARCHAR(50) NOT NULL, params JSON DEFAULT NULL, google_id VARCHAR(100) DEFAULT NULL, git_hub_id INT DEFAULT NULL, git_lab_id INT DEFAULT NULL, PRIMARY KEY (id))');
        $this->addSql('CREATE UNIQUE INDEX UNIQ_9C5F65BF772E836A ON system_user (identifier)');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('DROP SEQUENCE system_user_id_seq CASCADE');
        $this->addSql('DROP TABLE system_user');
    }
}
