# IFC Tileset (py3dtiles)

It is necessary to install py3dtiles and create the tileset before running Giro3D:

installation:
```sh
sudo apt install -y python3-venv

cd giro3d
python3 -m venv .venv
source .venv/bin/activate
pip install py3dtiles
pip install py3dtiles[ifc]

# later, reactivate with
source .venv/bin/activate
```

the complete Monteis scene creates an out-of-bounds exception, patch py3dtiles first (until we know root-cause / have upstream fix):
```
patch -p1 < ../bim/py3dtiles-ifc-material-id-fix.patch
```

for now, download the complete scene from Swisstopo https://confluence.swisstopo.ch/spaces/MON/pages/748978280/Visualisierung+BIM+IFC and put it here:
```
/bim/default_scene_complete.ifc 
```

build tiles:
```sh
rm -rf bim_tiles
py3dtiles convert --out="bim_tiles" ../bim/default_scene_complete.ifc 
```

# Giro3D

```sh
cd giro3d
npm install
npm run build
npm run start
```

Then open the local Vite URL shown in the terminal.
