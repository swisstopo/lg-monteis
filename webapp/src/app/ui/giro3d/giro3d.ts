import {
  AfterViewInit,
  Component,
  computed,
  effect,
  ElementRef,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import Instance from '@giro3d/giro3d/core/Instance.js';
import { CoordinateSystem } from '@giro3d/giro3d/core/geographic/CoordinateSystem.js';
import Tiles3D from '@giro3d/giro3d/entities/Tiles3D.js';
import { TranslatePipe } from '@ngx-translate/core';
import {
  AmbientLight,
  Color,
  DirectionalLight,
  GridHelper,
  Group,
  MathUtils,
  Mesh,
  MeshLambertMaterial,
  Object3D,
  SphereGeometry,
  Vector3,
} from 'three';
import { MapControls } from 'three/examples/jsm/controls/MapControls.js';
import { SensorResponseDto } from '../../core/generated';
import { InlineError } from '../inline-error/inline-error';
import { TilesFetch, TilesFetchPlugin } from './tiles-fetch-plugin';

const HIGHLIGHT_COLOR = new Color(0xa5f9a0);

@Component({
  imports: [TranslatePipe, MatProgressSpinner, InlineError],
  selector: 'app-giro3d',
  styleUrl: './giro3d.scss',
  templateUrl: './giro3d.html',
})
export class Giro3d implements AfterViewInit {
  readonly tilesetUrl = input.required<string | URL>();
  readonly sensors = input.required<SensorResponseDto[]>();

  /**
   * Performs the tileset and tile requests, see {@link TilesFetchPlugin}.
   */
  readonly fetch = input.required<TilesFetch>();

  private readonly view = viewChild.required<ElementRef<HTMLDivElement>>('view');

  protected readonly loading = signal(true);
  protected readonly error = signal(false);

  private readonly instance = signal<Instance | null>(null);
  private readonly tileset = computed(() => {
    const tileset = new Tiles3D({
      url: this.tilesetUrl().toString(),
      // Giro3d's own fetch plugin would win over ours if not disabled.
      enableFetchPlugin: false,
    });
    // Resolve the fetch function per request, and `untracked`, so that replacing it does not
    // rebuild the whole tileset.
    tileset.tiles.registerPlugin(
      new TilesFetchPlugin((url, options) => untracked(() => this.fetch())(url, options)),
    );
    return tileset;
  });
  private readonly controls = signal<MapControls | null>(null);

  private sensorGroup: Group | null = null;
  private readonly sensorsObj = computed(() => this.sensors().map(this.createSensorObject3D));

  private _mouseMoveEventListener: ((evt: MouseEvent) => void) | null = null;

  constructor() {
    effect((onCleanup) => {
      // Initialize/cleanup data whenever they change tilesetUrl changes
      const tileset = this.tileset();
      const instance = this.instance();
      const controls = this.controls();
      this.loading.set(true);
      if (!instance || !controls) return;

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
        .catch(this.showError.bind(this));

      onCleanup(() => {
        cancelled = true;
        tileset.removeEventListener('object-created', hideIfcSpaces);
        if (grid) instance.remove(grid);
        instance.remove(tileset);
      });
    });

    effect((onCleanup) => {
      // initialize / cleanup sensors whenever the list changes
      const instance = this.instance();
      const sensorsObj = this.sensorsObj();
      if (!instance || !this.sensorGroup || !sensorsObj) return;

      // cleanup sensors, because the list could have completely changed
      // this.cleanupSensors();
      for (let sensorObj of sensorsObj) {
        if (sensorObj) {
          this.sensorGroup.add(sensorObj);
        }
      }
      instance.notifyChange(this.sensorGroup);

      onCleanup(() => this.cleanupSensors);
    });
  }

  ngAfterViewInit(): void {
    try {
      this.initInstance();
    } catch (error) {
      this.showError(error);
    }
  }

  ngOnDestroy(): void {
    const instance = this.instance();
    if (instance) {
      instance.dispose();
      this.removeEventListeners(instance);
    }
    const controls = this.controls();
    if (controls) controls.dispose();
  }

  private cleanupSensors() {
    const instance = this.instance();
    if (instance && this.sensorGroup) {
      this.sensorGroup.traverse((o: Object3D) => {
        o.removeFromParent();
        if (o instanceof Mesh) {
          o.geometry.dispose();
          o.material.dispose();
        }
      });
      instance.notifyChange(this.sensorGroup);
    }
  }

  private setupLights(instance: Instance) {
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
  }

  private setupControls(instance: Instance) {
    const controls = new MapControls(instance.view.camera, instance.domElement);
    controls.enableDamping = true;
    controls.dampingFactor = 0.25;
    instance.view.setControls(controls);
    this.controls.set(controls);
  }

  private setupPicking(instance: Instance, sensorGroup: Group) {
    let highlightedElem: Mesh<SphereGeometry, MeshLambertMaterial> | null = null;

    function highlightElem(elem: Mesh<SphereGeometry, MeshLambertMaterial>) {
      highlightedElem = elem;
      elem.userData['originalColor'] = elem.material.color;
      elem.material.color = HIGHLIGHT_COLOR;
      elem.material.needsUpdate = true;
      instance.notifyChange(elem);
    }

    function resetHighlightedObject() {
      if (
        highlightedElem &&
        highlightedElem.material != null &&
        !Array.isArray(highlightedElem.material)
      ) {
        highlightedElem.material.color = highlightedElem.userData['originalColor'];
        highlightedElem.material.needsUpdate = true;
        instance.notifyChange(highlightedElem);
        highlightedElem = null;
      }
    }

    this._mouseMoveEventListener = (event) => {
      const picked = instance.pickObjectsAt(event, { sortByDistance: true });
      resetHighlightedObject();
      if (picked.length > 0) {
        // let's consider the first one in the picking order
        // we *don't* iterate, as we don't want to highlight a sensor that would be behind another object
        const object = picked[0].object;
        if (object === highlightedElem) {
          // nothing to do
          return;
        }
        if (object.parent === sensorGroup && object instanceof Mesh) {
          // highlight object
          highlightElem(object);
        }
      }
    };
    instance.domElement.addEventListener('mousemove', this._mouseMoveEventListener);
  }

  private initInstance() {
    const instance = new Instance({
      target: this.view().nativeElement,
      crs: CoordinateSystem.epsg3857,
      backgroundColor: 0xcccccc,
    });

    this.setupLights(instance);
    this.setupControls(instance);

    this.instance.set(instance);

    // set up group for sensors
    const sensorGroup = new Group();
    sensorGroup.name = 'sensorGroup';
    instance.add(sensorGroup);
    this.sensorGroup = sensorGroup;

    // set up listeners/picking/hovering etc.
    this.setupPicking(instance, sensorGroup);
  }

  private removeEventListeners(instance: Instance) {
    if (this._mouseMoveEventListener != null) {
      instance.domElement.removeEventListener('mousemove', this._mouseMoveEventListener);
    }
  }

  private createSensorObject3D(sensor: SensorResponseDto): Object3D | void {
    // create the geom
    let geom = new SphereGeometry(0.3, 32, 16);
    let sensor3D = new Mesh(geom, new MeshLambertMaterial({ color: 0x02cb02 }));
    if (sensor.coordinates == null) {
      return;
    }
    sensor3D.position.copy(sensor.coordinates);
    sensor3D.updateMatrixWorld();

    // set metadata
    sensor3D.userData = {
      name: sensor.name,
      comment: sensor.comment,
    };
    return sensor3D;
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

  private showError(error: unknown): void {
    this.error.set(true);
    this.loading.set(false);
    console.error('3D View Error: ', error);
  }
}
