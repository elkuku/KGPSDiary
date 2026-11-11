<?php

declare(strict_types=1);

namespace App\Tests\Api;

class CategoryTagApiTest extends ApiTestCase
{
    public function testCategoriesHaveADefaultColor(): void
    {
        $json = $this->api('GET', $this->create('/api/categories', ['name' => ' Hiking ']));

        self::assertSame('Hiking', $json['name']);
        self::assertSame('#2e7d32', $json['color']);
        self::assertNull($json['icon'] ?? null);
    }

    public function testValidatesCategories(): void
    {
        $json = $this->api('POST', '/api/categories', ['name' => '', 'color' => 'red', 'icon' => 'Not An Icon']);

        self::assertResponseStatusCodeSame(422);
        self::assertIsArray($json['violations']);
        $paths = \array_column($json['violations'], 'propertyPath');
        \sort($paths);
        self::assertSame(['color', 'icon', 'name'], $paths);
    }

    public function testTheSymbolIsOneOfTheCategorySymbols(): void
    {
        $json = $this->api('POST', '/api/categories', ['name' => 'Hikes', 'icon' => 'hiking']);
        self::assertResponseStatusCodeSame(422);
        self::assertSame('icon', $json['violations'][0]['propertyPath'] ?? null);

        $json = $this->api('GET', $this->create('/api/categories', ['name' => 'Walks', 'icon' => 'directions_walk']));
        self::assertSame('directions_walk', $json['icon']);
    }

    public function testNamesAreUniquePerUser(): void
    {
        foreach (['/api/categories', '/api/tags'] as $collection) {
            $this->create($collection, ['name' => 'Beach']);

            $json = $this->api('POST', $collection, ['name' => 'Beach']);
            self::assertResponseStatusCodeSame(422, $collection);
            self::assertSame('name', $json['violations'][0]['propertyPath'] ?? null);

            // Another user may have the same name
            $this->create($collection, ['name' => 'Beach'], as: 'admin');

            // Renaming to a name in use
            $other = $this->create($collection, ['name' => 'Mountains']);
            $this->api('PATCH', $other, ['name' => 'Beach']);
            self::assertResponseStatusCodeSame(422, $collection);

            // Saving a record with its own name
            $this->api('PATCH', $other, ['name' => 'Mountains']);
            self::assertResponseIsSuccessful();
        }
    }

    public function testUsersOnlySeeTheirOwn(): void
    {
        foreach (['/api/categories', '/api/tags'] as $collection) {
            $this->create($collection, ['name' => 'Mine']);
            $theirs = $this->create($collection, ['name' => 'Theirs'], as: 'admin');

            self::assertSame(['Mine'], self::titles($this->api('GET', $collection)));

            $this->api('DELETE', $theirs);
            self::assertResponseStatusCodeSame(404, $collection);
        }
    }

    public function testDeletingACategoryOrTagKeepsItsWaypoints(): void
    {
        $category = $this->create('/api/categories', ['name' => 'Food']);
        $tag = $this->create('/api/tags', ['name' => 'Lunch']);
        $waypoint = $this->create('/api/waypoints', ['title' => 'Café', 'latitude' => 0, 'longitude' => 0, 'category' => $category, 'tags' => [$tag]]);

        $this->api('DELETE', $category);
        self::assertResponseStatusCodeSame(204);
        $this->api('DELETE', $tag);
        self::assertResponseStatusCodeSame(204);

        $json = $this->api('GET', $waypoint);
        self::assertResponseIsSuccessful();
        self::assertNull($json['category'] ?? null);
        self::assertSame([], $json['tags']);
    }

    public function testCategoriesAndTagsAreSortedByName(): void
    {
        foreach (['/api/categories', '/api/tags'] as $collection) {
            $this->create($collection, ['name' => 'Zoo']);
            $this->create($collection, ['name' => 'Airport']);

            self::assertSame(['Airport', 'Zoo'], self::titles($this->api('GET', $collection)));
        }
    }
}
