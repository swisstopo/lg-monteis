#--------------------------------------------------------------------------------------------------------------
#-- get the full ifc scene from https://confluence.swisstopo.ch/spaces/MON/pages/748978280/Visualisierung+BIM+IFC
#--------------------------------------------------------------------------------------------------------------
unzip default_scene_complete.zip -d workspace/input

#--------------------------------------------------------------------------------------------------------------
#-- ifc4 files are not fully supported by mago-3d-tiler, convert them to GLB first
#--------------------------------------------------------------------------------------------------------------
curl -LO https://github.com/IfcOpenShell/IfcOpenShell/releases/download/ifcconvert-0.8.5/ifcconvert-0.8.5-linux64.zip
unzip ifcconvert-0.8.5-linux64.zip
./IfcConvert workspace/input/default_scene_complete.ifc workspace/glb/default_scene_complete.glb

#--------------------------------------------------------------------------------------------------------------
#-- create tileset for Giro3D
#--------------------------------------------------------------------------------------------------------------

docker pull gaia3d/mago-3d-tiler
rm -rf workspace/output
docker run --rm -v "./workspace:/workspace" -e JAVA_TOOL_OPTIONS="-Xmx16g" gaia3d/mago-3d-tiler -input /workspace/glb/ --inputType glb -output /workspace/output -d

# docker run --rm --user "$(id -u):$(id -g)" -e JAVA_TOOL_OPTIONS="-Xmx8g" -v "$PWD/workspace:/workspace" gaia3d/mago-3d-tiler -i /workspace/input -o /workspace/output -r -ot b3dm -tv 1.0 -tm explicit
