import { Controller } from '@hotwired/stimulus';
import mapboxgl from 'mapbox-gl';
import 'mapbox-gl/dist/mapbox-gl.min.css';

const SOURCE = 'waypoints';

/** A waypoint with a category symbol: its marker is bigger, the symbol white in it. */
const HAS_SYMBOL = ['!=', ['coalesce', ['get', 'icon'], ''], ''];
/** Symbol size in CSS pixels, drawn at twice the resolution. */
const SYMBOL_SIZE = 16;

/**
 * The user's waypoints on a Mapbox map, in their category colors and clustered when zoomed out.
 * A click on a waypoint shows it, a click elsewhere offers a new waypoint at that place.
 */
export default class extends Controller {
    static targets = ['map', 'category', 'tag', 'count'];

    static values = {
        token: String,
        featuresUrl: String,
        tracksUrl: String,
        newUrl: String,
        center: Array,
        labels: Object,
        /** The category symbols' 24×24 SVG paths, by name. */
        symbols: Object,
    };

    connect() {
        if (!this.tokenValue) {
            this.showMessage(this.labelsValue.noToken, 'warning');
            return;
        }

        mapboxgl.accessToken = this.tokenValue;
        this.map = new mapboxgl.Map({
            container: this.mapTarget,
            style: 'mapbox://styles/mapbox/outdoors-v12',
            center: this.hasCenterValue && this.centerValue.length === 2 ? this.centerValue : [0, 20],
            zoom: this.hasCenterValue && this.centerValue.length === 2 ? 14 : 1.5,
        });
        this.map.addControl(new mapboxgl.NavigationControl());
        this.map.addControl(new mapboxgl.GeolocateControl({ positionOptions: { enableHighAccuracy: true } }));
        this.map.addControl(new mapboxgl.ScaleControl());

        this.map.on('load', () => this.load());
        this.map.on('click', (event) => this.click(event));
    }

    disconnect() {
        this.map?.remove();
        this.map = null;
    }

    async load() {
        let data;
        try {
            const response = await fetch(this.featuresUrlValue, { headers: { Accept: 'application/geo+json' } });
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }
            data = await response.json();
        } catch (error) {
            console.error(error);
            this.showMessage(this.labelsValue.loadFailed, 'danger');
            return;
        }

        await this.loadTracks();

        this.features = data.features;
        this.addSymbolImages();
        this.map.addSource(SOURCE, { type: 'geojson', data: this.filtered(), cluster: true, clusterRadius: 40, clusterMaxZoom: 13 });
        this.showCount();
        this.map.addLayer({
            id: 'clusters',
            type: 'circle',
            source: SOURCE,
            filter: ['has', 'point_count'],
            paint: {
                'circle-color': '#455a64',
                'circle-radius': ['step', ['get', 'point_count'], 16, 10, 20, 50, 26],
                'circle-stroke-width': 2,
                'circle-stroke-color': '#ffffff',
            },
        });
        this.map.addLayer({
            id: 'cluster-count',
            type: 'symbol',
            source: SOURCE,
            filter: ['has', 'point_count'],
            layout: { 'text-field': ['get', 'point_count_abbreviated'], 'text-size': 13 },
            paint: { 'text-color': '#ffffff' },
        });
        this.map.addLayer({
            id: 'waypoints',
            type: 'circle',
            source: SOURCE,
            filter: ['!', ['has', 'point_count']],
            paint: {
                'circle-color': ['get', 'color'],
                'circle-radius': ['case', HAS_SYMBOL, 12, 8],
                'circle-stroke-width': 2,
                'circle-stroke-color': '#ffffff',
            },
        });
        this.map.addLayer({
            id: 'waypoint-symbols',
            type: 'symbol',
            source: SOURCE,
            filter: ['all', ['!', ['has', 'point_count']], HAS_SYMBOL],
            layout: {
                'icon-image': ['concat', 'symbol-', ['get', 'icon']],
                'icon-allow-overlap': true,
                'icon-ignore-placement': true,
            },
        });

        for (const layer of ['clusters', 'waypoints']) {
            this.map.on('mouseenter', layer, () => (this.map.getCanvas().style.cursor = 'pointer'));
            this.map.on('mouseleave', layer, () => (this.map.getCanvas().style.cursor = ''));
        }

        if (!(this.hasCenterValue && this.centerValue.length === 2) && data.features.length > 0) {
            const bounds = new mapboxgl.LngLatBounds();
            data.features.forEach((feature) => bounds.extend(feature.geometry.coordinates));
            this.map.fitBounds(bounds, { padding: 60, maxZoom: 14, duration: 0 });
        }
    }

    /** Each category symbol as a white image for the map, named symbol-<name>. */
    addSymbolImages() {
        const pixels = SYMBOL_SIZE * 2;
        for (const [name, path] of Object.entries(this.symbolsValue ?? {})) {
            const canvas = document.createElement('canvas');
            canvas.width = canvas.height = pixels;
            const context = canvas.getContext('2d');
            context.scale(pixels / 24, pixels / 24);
            context.fillStyle = '#ffffff';
            context.fill(new Path2D(path));
            this.map.addImage(`symbol-${name}`, context.getImageData(0, 0, pixels, pixels), { pixelRatio: 2 });
        }
    }

    /** The recorded tracks as lines, under the waypoints. */
    async loadTracks() {
        if (!this.tracksUrlValue) return;

        try {
            const response = await fetch(this.tracksUrlValue, { headers: { Accept: 'application/geo+json' } });
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            const tracks = await response.json();
            this.map.addSource('tracks', { type: 'geojson', data: tracks });
            this.map.addLayer({
                id: 'tracks',
                type: 'line',
                source: 'tracks',
                layout: { 'line-join': 'round', 'line-cap': 'round' },
                paint: { 'line-color': '#6a1b9a', 'line-width': 4, 'line-opacity': 0.8 },
            });
        } catch (error) {
            console.error(error);
        }
    }

    /** Shows only the waypoints of the chosen category and tag. */
    filter() {
        this.map?.getSource(SOURCE)?.setData(this.filtered());
        this.showCount();
    }

    filtered() {
        const category = this.hasCategoryTarget ? this.categoryTarget.value : '';
        const tag = this.hasTagTarget ? Number(this.tagTarget.value) : 0;

        const features = (this.features ?? []).filter(({ properties }) => {
            if (category === 'none' && properties.categoryId !== null) return false;
            if (category && category !== 'none' && properties.categoryId !== Number(category)) return false;
            return !tag || properties.tagIds.includes(tag);
        });

        return { type: 'FeatureCollection', features };
    }

    showCount() {
        if (this.hasCountTarget && this.features) {
            this.countTarget.textContent = `${this.filtered().features.length} / ${this.features.length}`;
        }
    }

    click(event) {
        const layers = ['clusters', 'waypoints'].filter((layer) => this.map.getLayer(layer));
        const [feature] = layers.length ? this.map.queryRenderedFeatures(event.point, { layers }) : [];

        if (feature?.properties.cluster) {
            this.map.getSource(SOURCE).getClusterExpansionZoom(feature.properties.cluster_id, (error, zoom) => {
                if (!error) {
                    this.map.easeTo({ center: feature.geometry.coordinates, zoom });
                }
            });
        } else if (feature) {
            this.popup(feature.geometry.coordinates, this.waypointContent(feature.properties));
        } else {
            this.popup(event.lngLat, this.newWaypointContent(event.lngLat));
        }
    }

    popup(lngLat, content) {
        this.currentPopup?.remove();
        this.currentPopup = new mapboxgl.Popup({ offset: 12 }).setLngLat(lngLat).setDOMContent(content).addTo(this.map);
    }

    waypointContent(properties) {
        const content = document.createElement('div');
        const title = document.createElement('strong');
        title.textContent = properties.title;
        content.append(title);

        const details = [properties.category, properties.visitedAt].filter(Boolean).join(' · ');
        if (details) {
            const small = document.createElement('div');
            small.className = 'small text-body-secondary';
            small.textContent = details;
            content.append(small);
        }

        content.append(this.link(properties.url, this.labelsValue.open));
        return content;
    }

    newWaypointContent(lngLat) {
        const url = new URL(this.newUrlValue, window.location.origin);
        url.searchParams.set('latitude', lngLat.lat.toFixed(6));
        url.searchParams.set('longitude', lngLat.lng.toFixed(6));

        const content = document.createElement('div');
        const position = document.createElement('div');
        position.className = 'small font-monospace';
        position.textContent = `${lngLat.lat.toFixed(5)}, ${lngLat.lng.toFixed(5)}`;
        content.append(position, this.link(url.toString(), this.labelsValue.addHere));
        return content;
    }

    link(href, text) {
        const link = document.createElement('a');
        link.href = href;
        link.className = 'd-block mt-1';
        link.textContent = text;
        return link;
    }

    showMessage(text, type) {
        const alert = document.createElement('div');
        alert.className = `alert alert-${type} m-3`;
        alert.textContent = text;
        (this.hasMapTarget ? this.mapTarget : this.element).replaceChildren(alert);
    }
}
