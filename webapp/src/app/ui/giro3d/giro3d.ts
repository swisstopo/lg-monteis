import {
  AfterViewInit,
  Component,
  computed,
  effect,
  ElementRef,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import Instance from '@giro3d/giro3d/core/Instance.js';
import { CoordinateSystem } from '@giro3d/giro3d/core/geographic/CoordinateSystem.js';
import Tiles3D from '@giro3d/giro3d/entities/Tiles3D.js';
import { TranslatePipe } from '@ngx-translate/core';
import { AmbientLight, DirectionalLight, GridHelper, MathUtils, Object3D, Vector3 } from 'three';
import { MapControls } from 'three/examples/jsm/controls/MapControls.js';

@Component({
  imports: [TranslatePipe, MatProgressSpinner],
  selector: 'app-giro3d',
  styleUrl: './giro3d.scss',
  templateUrl: './giro3d.html',
})
export class Giro3d implements AfterViewInit {
  readonly tilesetUrl = input.required<string | URL>();

  private readonly view = viewChild.required<ElementRef<HTMLDivElement>>('view');

  protected readonly loading = signal(true);
  private readonly instance = signal<Instance | null>(null);
  private readonly tileset = computed(() => new Tiles3D({ url: this.tilesetUrl().toString() }));
  private readonly controls = signal<MapControls | null>(null);

  constructor() {
    // Initialize/cleanup tileset whenever the tilesetUrl changes
    effect((onCleanup) => {
      this.loading.set(true);
      const instance = this.instance();
      const controls = this.controls();
      if (!instance || !controls) return;

      const tileset = this.tileset();
      let cancelled = false;
      let grid: GridHelper | undefined;

      // If the tileset comes from an ifc converted with py3dtiles, hide some elements
      // that don't bring visual value
      const hideIfcSpaces = (event: { obj: Object3D }) => {
        event.obj.traverse((obj) => {
          if (obj.userData?.['class'] === 'IfcSpace') {
            obj.visible = false;
            instance.notifyChange();
          }
        });
      };
      tileset.addEventListener('object-created', hideIfcSpaces);

      instance
        .add(tileset)
        .then(() => {
          if (cancelled) {
            instance.remove(tileset);
            return;
          }
          grid = this.initCamera(instance, controls, tileset);
        })
        .finally(() => {
          this.loading.set(false);
        })
        .catch((error: unknown) => this.showError(error));

      onCleanup(() => {
        cancelled = true;
        tileset.removeEventListener('object-created', hideIfcSpaces);
        if (grid) instance.remove(grid);
        instance.remove(tileset);
      });
    });
  }

  ngAfterViewInit(): void {
    this.initInstance();
  }

  ngOnDestroy(): void {
    this.controls()?.dispose();
    this.instance()?.dispose();
  }

  private initInstance() {
    const instance = new Instance({
      target: this.view().nativeElement,
      crs: CoordinateSystem.epsg3857,
      backgroundColor: 0xcccccc,
    });

    // Add a sunlight
    const sun = new DirectionalLight('#ffffff', 1.4);
    sun.position.set(1, 0, 1).normalize();
    sun.updateMatrixWorld(true);
    instance.scene.add(sun);

    // We can look below the floor, so let's light also a bit there
    const sun2 = new DirectionalLight('#ffffff', 0.5);
    sun2.position.set(0, -1, 1);
    sun2.updateMatrixWorld();
    instance.scene.add(sun2);

    // Add ambient light
    const ambientLight = new AmbientLight(0xffffff, 1);
    instance.scene.add(ambientLight);
    instance.view.minNearPlane = 0.5;

    const controls = new MapControls(instance.view.camera, instance.domElement);
    controls.enableDamping = true;
    controls.dampingFactor = 0.25;
    instance.view.setControls(controls);

    this.instance.set(instance);
    this.controls.set(controls);
  }

  private initCamera(
    instance: Instance,
    controls: MapControls,
    tileset: Tiles3D,
  ): GridHelper | undefined {
    const bbox = tileset.getBoundingBox();
    if (!bbox) {
      console.warn('Bounding box of tileset could not be computed');
      return;
    }

    const tmpVec3 = new Vector3();
    const ratio = bbox.getSize(tmpVec3).x / bbox.getSize(tmpVec3).z;

    const position = bbox
      .getCenter(new Vector3())
      .clone()
      .add(bbox.getSize(tmpVec3).multiply(new Vector3(-2, -2, ratio)));

    const lookAt = bbox.getCenter(tmpVec3);
    lookAt.z = bbox.min.z;

    this.placeCamera(instance, controls, position, lookAt);

    const grid = new GridHelper(60, 10);
    grid.rotateX(MathUtils.degToRad(90));

    grid.position.copy(lookAt);

    instance.add(grid);
    grid.updateMatrixWorld(true);

    return grid;
  }

  private placeCamera(
    instance: Instance,
    controls: MapControls,
    position: Vector3,
    lookAt: Vector3,
  ) {
    instance.view.camera.position.set(position.x, position.y, position.z);
    instance.view.camera.lookAt(lookAt);
    controls.target.copy(lookAt);
    instance.notifyChange(instance.view.camera);
  }

  private showError(event: unknown): void {
    // TODO
    console.error('Error: ', event);
  }
}
