import { HttpClient } from '@angular/common/http';
import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  ViewChild,
  effect,
  inject,
  signal,
} from '@angular/core';
import Instance from '@giro3d/giro3d/core/Instance.js';
import { CoordinateSystem } from '@giro3d/giro3d/core/geographic/CoordinateSystem.js';
import Tiles3D from '@giro3d/giro3d/entities/Tiles3D.js';
import { WorkbenchView } from '@scion/workbench';
import { firstValueFrom } from 'rxjs';
import {
  AmbientLight,
  AxesHelper,
  Box3,
  DirectionalLight,
  GridHelper,
  PerspectiveCamera,
  Vector3,
} from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import type { TilingProgressPhase } from './tiling/ifc-tiler';
import type { OctreeConfig } from './tiling/octree';
import type { TilingWorkerRequest, TilingWorkerResponse } from './tiling/tiling.worker';

const TILESET_URL = '/model/tiles/tileset.json';
const TILESET_RETRY_DELAY_MS = 2000;
const TILE_UPLOAD_URL = '/api/tiles';

// Matches the defaults the native monteis-tiling binary uses (see /tiling/README.md), so tilesets
// produced client-side and server-side are comparably sized.
const TILING_CONFIG: OctreeConfig = { maxElementsPerTile: 50, maxDepth: 8 };

const PHASE_LABELS: Record<TilingProgressPhase, string> = {
  parsing: 'Parsing IFC file...',
  triangulating: 'Triangulating geometry...',
  partitioning: 'Building spatial tiles...',
  writing: 'Writing glTF tiles...',
};

@Component({
  selector: 'app-poc',
  imports: [],
  templateUrl: './poc.html',
  styleUrl: './poc.scss',
})
export default class Poc implements AfterViewInit, OnDestroy {
  @ViewChild('container', { static: true })
  private readonly containerRef!: ElementRef<HTMLDivElement>;

  private readonly http = inject(HttpClient);

  protected readonly busy = signal(false);
  protected readonly statusMessage = signal<string | null>(null);
  protected readonly errorMessage = signal<string | null>(null);

  private instance?: Instance;
  private camera?: PerspectiveCamera;
  private controls?: OrbitControls;
  private tiles3d?: Tiles3D;
  private gridHelper?: GridHelper;
  private animationFrameId?: number;
  private tilingWorker?: Worker;
  private destroyed = false;

  constructor(view: WorkbenchView) {
    effect(() => {
      view.title = 'Digital Twin';
    });
  }

  ngAfterViewInit(): void {
    // Placeholder near/far/position for the brief window before a tileset loads - frameCameraOnBox
    // replaces all of this with values based on the model's actual size once it's known, since a
    // fixed guess here is wrong by orders of magnitude for some models and not others (see
    // frameCameraOnBox for why that matters beyond just the initial framing).
    const camera = new PerspectiveCamera(60, undefined, 0.1, 10000);
    this.camera = camera;

    const instance = new Instance({
      target: this.containerRef.nativeElement,
      // Non-georeferenced cartesian scene, as is the case for a single IFC model.
      crs: CoordinateSystem.unknown,
      backgroundColor: '#ffffff',
      camera,
    });
    this.instance = instance;

    camera.position.set(15, 12, 15);
    camera.lookAt(0, 0, 0);

    const controls = new OrbitControls(camera, instance.domElement);
    controls.target.set(0, 0, 0);
    controls.enableDamping = true;
    controls.addEventListener('change', () => instance.notifyChange());
    this.controls = controls;

    instance.scene.add(new AmbientLight(0xffffff, 0.6));
    const sun = new DirectionalLight(0xffffff, 1.2);
    sun.position.set(10, 20, 10);
    instance.scene.add(sun);

    this.gridHelper = new GridHelper(20, 20, 0x666666, 0x333333);
    instance.scene.add(this.gridHelper);
    instance.scene.add(new AxesHelper(5));
    instance.notifyChange();

    this.tick();

    this.loadTiles().catch((error) => {
      console.error('Failed to load tiles', error);
    });
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    if (this.animationFrameId !== undefined) {
      cancelAnimationFrame(this.animationFrameId);
    }
    this.tilingWorker?.terminate();
    this.controls?.dispose();
    this.tiles3d?.dispose();
    this.gridHelper?.geometry.dispose();
    if (Array.isArray(this.gridHelper?.material)) {
      this.gridHelper.material.forEach((material) => material.dispose());
    } else {
      this.gridHelper?.material.dispose();
    }
    this.instance?.dispose();
  }

  /**
   * Reads the selected IFC file, tiles it in a Web Worker (parsing, triangulation and octree
   * partitioning all happen off the main thread via web-ifc/WASM - see ./tiling/tiling.worker.ts),
   * uploads the resulting tileset to the backend, then reloads the viewer against it.
   */
  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = ''; // allow re-selecting the same file later
    if (!file) {
      return;
    }

    this.errorMessage.set(null);
    this.busy.set(true);
    this.statusMessage.set('Reading file...');

    file
      .arrayBuffer()
      .then((buffer) => this.runTilingWorker(buffer))
      .catch((error: unknown) => this.fail(error));
  }

  private runTilingWorker(ifcBytes: ArrayBuffer): void {
    const worker = new Worker(new URL('./tiling/tiling.worker', import.meta.url), {
      type: 'module',
    });
    this.tilingWorker = worker;

    worker.onmessage = ({ data }: MessageEvent<TilingWorkerResponse>) => {
      switch (data.type) {
        case 'progress':
          this.statusMessage.set(PHASE_LABELS[data.phase]);
          break;
        case 'done':
          this.statusMessage.set('Uploading tiles...');
          this.uploadTiles(data.files, data.skippedCount).finally(() => this.disposeTilingWorker());
          break;
        case 'error':
          this.fail(data.message);
          this.disposeTilingWorker();
          break;
      }
    };
    worker.onerror = (event: ErrorEvent) => {
      this.fail(event.message || 'Tiling worker crashed');
      this.disposeTilingWorker();
    };

    const request: TilingWorkerRequest = { ifcBytes, config: TILING_CONFIG };
    worker.postMessage(request, [ifcBytes]);
  }

  private disposeTilingWorker(): void {
    this.tilingWorker?.terminate();
    this.tilingWorker = undefined;
  }

  private async uploadTiles(
    files: { name: string; data: ArrayBuffer }[],
    skippedCount: number,
  ): Promise<void> {
    const formData = new FormData();
    for (const file of files) {
      formData.append('files', new Blob([file.data]), file.name);
    }

    try {
      await firstValueFrom(this.http.post(TILE_UPLOAD_URL, formData));
      this.statusMessage.set('Reloading viewer...');
      await this.loadTiles();
      this.statusMessage.set(
        skippedCount > 0
          ? `Done - ${skippedCount} element(s) could not be triangulated and were skipped.`
          : 'Done.',
      );
    } catch (error) {
      this.fail(error);
    } finally {
      this.busy.set(false);
    }
  }

  private fail(error: unknown): void {
    this.errorMessage.set(error instanceof Error ? error.message : String(error));
    this.busy.set(false);
  }

  /**
   * Tiles are streamed straight off the current 3D Tiles tileset - giro3d's Tiles3D entity
   * (backed by 3d-tiles-renderer) owns fetching, LOD selection and disposal of individual tiles
   * from here on; the frontend never touches raw glTF itself. Safe to call again after an
   * upload replaces the tileset: any previous Tiles3D is disposed first.
   */
  private async loadTiles(): Promise<void> {
    await this.waitForTilesetReady();
    if (this.destroyed || !this.instance) {
      return;
    }

    if (this.tiles3d) {
      this.instance.remove(this.tiles3d);
      this.tiles3d.dispose();
      this.tiles3d = undefined;
    }

    const tiles3d = new Tiles3D({ url: TILESET_URL });
    this.tiles3d = tiles3d;
    await this.instance.add(tiles3d);
    if (this.destroyed) {
      return;
    }

    const box = this.groundEntity(tiles3d);
    if (box) {
      this.frameCameraOnBox(box);
    }
    this.instance.notifyChange();
  }

  /** The startup tiling run happens in the background, so the backend answers 503 until the
   * tileset is ready - poll until it flips to 200 rather than handing giro3d a URL that 404s. */
  private async waitForTilesetReady(): Promise<void> {
    while (!this.destroyed) {
      const response = await fetch(TILESET_URL, { method: 'HEAD' });
      if (response.ok) {
        return;
      }
      if (response.status !== 503) {
        throw new Error(`Could not fetch tileset.json: ${response.status} ${response.statusText}`);
      }
      await new Promise((resolve) => setTimeout(resolve, TILESET_RETRY_DELAY_MS));
    }
    throw new Error('Component destroyed while waiting for tiles');
  }

  /**
   * Recenters the tileset horizontally and drops it onto the Y=0 plane, so that its lowest point
   * (the tunnel's starting/ground level) sits on the grid instead of wherever the tiler's own
   * coordinates happened to leave it. Returns the resulting world-space bounding box, or `null`
   * if the tileset reported no bounds.
   */
  private groundEntity(tiles3d: Tiles3D): Box3 | null {
    const box = tiles3d.getBoundingBox();
    if (!box || box.isEmpty()) {
      return null;
    }

    const center = box.getCenter(new Vector3());
    tiles3d.object3d.position.x -= center.x;
    tiles3d.object3d.position.z -= center.z;
    tiles3d.object3d.position.y -= box.min.y;
    tiles3d.object3d.updateMatrixWorld();

    return box.clone().translate(tiles3d.object3d.position);
  }

  /**
   * Points the camera at the loaded model and, crucially, scales the camera's near/far planes
   * and the controls' zoom range to the model's actual size. The fixed near=0.1/far=10000 the
   * camera is constructed with is tuned for nothing in particular: for a model spanning hundreds
   * of units it leaves too little depth-buffer precision at any real viewing distance (z-fighting
   * that reads as "can't really control it with the mouse" - things swim as you orbit), and for a
   * small one it can't zoom in past the near plane at all. Scaling both to the model's radius
   * fixes both directions at once, whatever the model's actual scale turns out to be.
   *
   * The `|| 1` floors used here previously were meant only to guard the degenerate empty-box
   * case, but `Math.max(actualSize, 1)` silently clamps a genuinely small model (e.g. one small
   * borehole, a few units across) back up to "at least 1 unit", which is exactly wrong: it's what
   * made the grid look oversized and capped how far you could zoom in. Only fall back to a fixed
   * size for a truly zero-size box.
   */
  private frameCameraOnBox(box: Box3): void {
    if (!this.instance || !this.controls || !this.camera) {
      return;
    }

    const center = box.getCenter(new Vector3());
    const size = box.getSize(new Vector3());
    const maxDimension = Math.max(size.x, size.y, size.z) || 1;
    const radius = maxDimension * 0.5;

    this.camera.near = radius / 1000;
    this.camera.far = radius * 100;
    this.camera.updateProjectionMatrix();

    this.controls.minDistance = radius / 100;
    this.controls.maxDistance = radius * 20;

    this.controls.target.copy(center);
    this.camera.position.set(center.x + radius * 2, center.y + radius * 1.5, center.z + radius * 2);
    this.camera.lookAt(center);
    this.controls.update();

    this.updateGrid(center, size);
  }

  /** Resizes the ground grid to the model's actual footprint - a grid sized for a small demo
   * scene is an invisible speck next to a large real model (and vice versa), which looks like
   * the ground plane simply isn't where the model is. See frameCameraOnBox for why this no
   * longer clamps small models up to a fixed minimum size either. */
  private updateGrid(center: Vector3, size: Vector3): void {
    if (!this.instance) {
      return;
    }

    if (this.gridHelper) {
      this.instance.remove(this.gridHelper);
      this.gridHelper.geometry.dispose();
      if (Array.isArray(this.gridHelper.material)) {
        this.gridHelper.material.forEach((material) => material.dispose());
      } else {
        this.gridHelper.material.dispose();
      }
    }

    const footprint = Math.max(size.x, size.z) || 1;
    const gridSize = footprint * 1.5;
    const gridHelper = new GridHelper(gridSize, 20, 0x666666, 0x333333);
    gridHelper.position.set(center.x, 0, center.z);
    this.gridHelper = gridHelper;
    this.instance.scene.add(gridHelper);
  }

  private tick = (): void => {
    if (!this.instance || !this.controls) {
      return;
    }
    this.controls.update();
    this.animationFrameId = requestAnimationFrame(this.tick);
  };
}
