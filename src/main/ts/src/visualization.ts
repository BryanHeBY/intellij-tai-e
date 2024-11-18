import go, { Group, Model } from 'gojs';
import { Graph } from './graph' ;
import { Alias } from 'yaml';
import { group } from 'console';

const $ = go.GraphObject.make;

const myDiagram =
new go.Diagram("myDiagramDiv",
    {
        padding: 10,
        layout: $(go.LayeredDigraphLayout,
            {
                direction: 90,
                layeringOption: go.LayeredDigraphLayout.LayerLongestPathSource,
                alignOption: go.LayeredDigraphLayout.AlignAll
            }),
        "undoManager.isEnabled": true
    });

export function visualize(graph: Graph){
    const nodeDataArray = makeNodes(graph);
    const linkDataArray = makeLinks(graph);

    myDiagram.model = new go.GraphLinksModel(nodeDataArray, linkDataArray);

    myDiagram.nodeTemplate = 
        $(go.Node, "Auto",
            new go.Binding("visible"),
            $(go.Shape, "Rectangle", {fill: "lightblue"}, 
                new go.Binding("fill", "color"),
                new go.Binding("stroke", "isHighlighted", h => h ? "red" : "black").ofObject(),
                new go.Binding("strokeWidth", "isHighlighted", h => h ? 4 : 2).ofObject(),
            ),
            $(go.TextBlock, "isla", { margin: 10 },
                new go.Binding("text", "key")
            ),
            /*
            
            */
            $("Button",  // a replacement for "TreeExpanderButton" that works for non-tree-structured graphs
                // assume initially not visible because there are no links coming out
                { visible: false, alignment: go.Spot.BottomCenter },
                // bind the button visibility to whether it's not a leaf node
                new go.Binding("visible", "isTreeLeaf", leaf => !leaf).ofObject(),
                $(go.Shape,
                    {
                        name: "ButtonIcon",
                        figure: "MinusLine",
                        desiredSize: new go.Size(7, 7)
                    },
                new go.Binding("figure", "isCollapsed",  // data.isCollapsed remembers "collapsed" or "expanded"
                                collapsed => collapsed ? "PlusLine" : "MinusLine")),
                {
                    click: (e, obj) => {
                        e.diagram.startTransaction();
                        var node = obj.part as go.Node;
                        if (node.data.isCollapsed) {
                            expandFrom(node, node);
                        } else {
                            collapseFrom(node, node);
                        }
                        e.diagram.commitTransaction("toggled visibility of dependencies");
                    }
                }
            )
        );
    
    myDiagram.linkTemplate = 
        $(go.Link,
            { curve: go.Link.Bezier },
            $(go.Shape, { strokeWidth: 3 },
                new go.Binding("stroke", "isHighlighted", h => h ? "red" : "black").ofObject(),
                new go.Binding("strokeWidth", "isHighlighted", h => h ? 4 : 2).ofObject(),
            ),
            $(go.Shape, { toArrow: "Standard", strokeWidth: 3 },
                new go.Binding("stroke", "isHighlighted", h => h ? "red" : "black").ofObject(),
            ),
        );

    myDiagram.groupTemplate =
        $(go.Group, "Auto",
            {
                // layout: $(go.GridLayout,
                // { wrappingColumn: 2, arrangement: go.GridLayout.Ascending, spacing: new go.Size(50,50), isRealtime: false }),
                // isSubGraphExpanded: false,
                layout: $(go.LayeredDigraphLayout,
                    {
                      direction: 90,
                      layeringOption: go.LayeredDigraphLayout.LayerLongestPathSource,
                      alignOption: go.LayeredDigraphLayout.AlignAll
                    }),
                    isSubGraphExpanded: false
            },
            $(go.Shape, "RoundedRectangle",
                { fill: null, stroke: "gray", strokeWidth: 2 }),
            $(go.Panel, "Vertical",
                { defaultAlignment: go.Spot.Left, margin: 4 },
                $(go.Panel, "Horizontal",
                    { defaultAlignment: go.Spot.Top },
                    $("SubGraphExpanderButton"),
                    $(go.TextBlock,
                        { font: "Bold 18px Sans-Serif", margin: 4 },
                        new go.Binding("text", "key")),
                ),
                $(go.Placeholder,
                { padding: new go.Margin(0, 10) }),
            ),
            {
                selectionAdornmentTemplate:
                  $(go.Adornment, "Spot",
                    $(go.Panel, "Auto",
                        $(go.Shape, { fill: null, stroke: "black", strokeWidth: 4 }),
                        $(go.Placeholder),
                    ),
                    $("Button",
                        $(go.TextBlock, "go-to-def"),
                        { alignment: go.Spot.Top, alignmentFocus: go.Spot.Center, desiredSize: new go.Size(80,25), click: goToDef },
                    )
                  ),
            },
        );
    myDiagram.nodes.each(function(n) {
        // myDiagram.startTransaction();
        n.visible = false; 
        // myDiagram.commitTransaction("hide all nodes");
    })
    myDiagram.nodes.each(function(n) {
        if(n instanceof go.Node && !(n instanceof go.Group) && !n.findLinksInto().next()){
            var nd = n as go.Node;
            nd.visible = true;
            while(nd.containingGroup !== null){
                (nd.containingGroup as go.Group).visible = true;
                nd = nd.containingGroup;
            }
        }
    })
    myDiagram.links.each(link => link.visible = false);

    if(!buttonCreated){
        
        LayersChangeButton("Remove Package", "Restore Package", graph, restorePackages, removePackages);
        LayersChangeButton("Remove Classes", "Restore Class", graph, restoreClasses, removeClasses);
        LayersChangeButton("Remove Methods", "Restore Methods", graph, restoreMethods, removeMethods);
        //HighlightPathButton("Highlight Recommanded Paths", graph, highlightRecommandedPath);
        //ResetButton(graph, hideAll);
        
        buttonCreated = true;
    }
    
}

// ===================== LayersChangeButton Function =========================

var buttonCreated = false;
// 记录各层次是否还在图中出现，如packageExist = false代表图中不出现Package层次
let packageExist = true;
let classExist = true;
let methodExist = true;

function LayersChangeButton(restoreState : string, removeState : string, graph : Graph, restoreFunc : (graph : Graph) => void, removeFunc : (graph : Graph) => void){
    var button = document.createElement("button");
    button.innerHTML = restoreState;
    document.getElementById("buttonContainer")?.appendChild(button);
    button.addEventListener("click", function(){
        if(button.innerHTML === restoreState){
            button.innerHTML = removeState;
            removeFunc(graph);
        }
        else if(button.innerHTML === removeState){
            button.innerHTML = restoreState;
            restoreFunc(graph);
        }
    })
}


function removePackages(graph : Graph){
    myDiagram.startTransaction("remove Package");
    graph.metadata.groups.get("package")?.forEach(idx => removeGroup(graph.metadata.get(idx).name));
    myDiagram.commitTransaction("remove Package");
    packageExist = false;
}

function removeClasses(graph : Graph){
    myDiagram.startTransaction("remove Class");
    graph.metadata.groups.get("class")?.forEach(idx => removeGroup(graph.metadata.get(idx).name));
    myDiagram.commitTransaction("remove Class");
    classExist = false;
}

function removeMethods(graph : Graph){
    myDiagram.startTransaction("remove Method");
    graph.metadata.groups.get("method")?.forEach(idx => removeGroup(graph.metadata.get(idx).name));
    myDiagram.commitTransaction("remove Method");
    methodExist = false;
}

function removeGroup(name: string){
    var group = myDiagram.findNodeForKey(name) as go.Group;
    if(group === null) return;

    (group.diagram as go.Diagram).model.setDataProperty(group, "isSubGraphExpanded", true);
    group.memberParts.each(nodeOrGroup => (nodeOrGroup.diagram as go.Diagram).model.setDataProperty(nodeOrGroup.data, "group", group.containingGroup? group.containingGroup.key : null));
    myDiagram.model.removeNodeData(myDiagram.model.findNodeDataForKey(name) as go.ObjectData);
}


function restorePackages(graph : Graph){
    myDiagram.startTransaction("restore Package");
    // 恢复Package层需要先恢复Class层，而后再将Class层删去，做到只恢复Package层的效果
    function execute(){
        graph.metadata.groups.get("package")!.forEach(kid => {
            const k = graph.metadata.get(kid).name;
            const vv = graph.children.get(kid)!.filter(v => v !== undefined).map(v => graph.metadata.get(v).name);
            restoreGroup(k, vv);
        });
    }
    if(!classExist){ 
        restoreClasses(graph);
        execute();
        removeClasses(graph);
    }else{
        execute();
    }
    myDiagram.commitTransaction("restore Package");
    packageExist = true;
}

function restoreClasses(graph : Graph){
    myDiagram.startTransaction("restore Class");
    // 恢复Class层需要先恢复Method层，理由同上
    function execute(){
        graph.metadata.groups.get("class")!.forEach(kid => {
            const k = graph.metadata.get(kid).name;
            const vv = graph.children.get(kid)!.filter(v => v !== undefined).map(v => graph.metadata.get(v).name);
            restoreGroup(k, vv);
        });
    }
    if(!methodExist){
        restoreMethods(graph);
        execute();
        removeMethods(graph);
    }else{
        execute();
    }
    myDiagram.commitTransaction("restore Class");
    classExist = true;
}

function restoreMethods(graph : Graph){
    myDiagram.startTransaction("restore Method");
    function execute(){
        graph.metadata.groups.get("method")!.forEach(kid => {
            const k = graph.metadata.get(kid).name;
            const vv = graph.children.get(kid)!.filter(v => v !== undefined).map(v => graph.metadata.get(v).name);
            restoreGroup(k, vv);
        });
    }
    execute();
    myDiagram.commitTransaction("restore Method");
    methodExist = true;
}

function restoreGroup(name: string, members : string[]){
    myDiagram.model.addNodeData({key: name, isGroup: true, isHighlighted: false, isCollapsed: false});

    const parent = myDiagram.findNodeForKey(name) as go.Group;  // 要恢复的Group
    let containGroupKey;

    // 将要恢复的Group展开
    (parent.diagram as go.Diagram).model.setDataProperty(parent, "isSubGraphExpanded", true);
    members.forEach(n=>{
        var node = myDiagram.findNodeForKey(n) as go.Node;
        if(node === null){
            console.error("No such node found in Diagram");
            return;
        }
        containGroupKey = node.containingGroup?.key;
        (node.diagram as go.Diagram).model.setDataProperty(node.data, "group", name);
    });

    // 如果发现恢复的Group内部没有任何成员可见，那么折叠这个Group并使其不可见
    if(parent.memberParts.all(m => !m.isVisible())){
        (parent.diagram as go.Diagram).model.setDataProperty(parent, "isSubGraphExpanded", false);
        (parent.diagram as go.Diagram).model.setDataProperty(parent, "visible", false);
    }

    // 设置恢复Group的containingGroup
    (parent.diagram as go.Diagram).model.setDataProperty(parent.data, "group", containGroupKey);
}


// ===============================================================
// ===============================================================

myDiagram.click = e => {
    e.diagram.commit(d => d.clearHighlighteds(), "clear highlights");
}

// ====================== Fold & Expand Event ====================
function collapseFrom(node: go.Node, start: go.Node) {
    // 若该节点还有可见的入边，则保留
    if (node.findLinksInto().any(link => link.visible) && node !== start){
        return;
    }

    if (node.data.isCollapsed) {
        collapse(node);
        return;
    }

    (node.diagram as go.Diagram).model.setDataProperty(node.data, "isCollapsed", true);

    if (node !== start) {
        collapse(node);
    }
    node.findLinksOutOf().each(link => (link.diagram as go.Diagram).model.setDataProperty(link, "visible", false))
    node.findNodesOutOf().each(n => collapseFrom(n, start));

    function collapse(node: go.Node){
        (node.diagram as go.Diagram).model.setDataProperty(node.data, "visible", false);
        
        // 若Group内没有可见的Node，则Group也隐藏
        var cg = node.containingGroup;
        while(cg !== null){
            if (cg.memberParts.any(n => n.visible)) {
                break;
            } 

            (cg.diagram as go.Diagram).model.setDataProperty(cg, "visible", false);
            cg = cg.containingGroup;
        }
    }
}

function expandFrom(node: go.Node, start: go.Node) {
    if (!node.data.isCollapsed) 
        return;

    (node.diagram as go.Diagram).model.setDataProperty(node.data, "isCollapsed", false);

    node.findNodesOutOf().each(n => {
            (n.diagram as go.Diagram).model.setDataProperty(n.data, "visible", true);

            // 若有Node变为可见，则Node所在的Group也得可见
            var cg = n.containingGroup;
            while(cg !== null && cg.visible !== true){
                console.log(cg.key); // FIXME
                (cg.diagram as go.Diagram).model.setDataProperty(cg, "visible", true);
                cg = cg.containingGroup;
            }
        }
    )
    node.findLinksOutOf().each(link => (link.diagram as go.Diagram).model.setDataProperty(link, "visible", true))
}
// ===============================================================


function highlightPath(event: go.InputEvent, ador: go.GraphObject){
    const diagram = ador.diagram as go.Diagram;
    const node = ((ador.part as go.Adornment).adornedPart) as go.Node;
    diagram.startTransaction("highlight");
    diagram.clearHighlighteds();
    diagram.findLayer("Foreground")?.parts.each(p=>{
        if(p instanceof go.Link){
            p.layerName = "";
        }
    })
    mark(node, 4);
    diagram.commitTransaction("highlight");
}

function mark(node: go.Node, depth: number){
    if(depth === 0) return;

    node.isHighlighted = true;
    node.findLinksOutOf().each(l =>{
        l.isHighlighted = true;
        l.layerName = "Foreground";
    });
    node.findNodesOutOf().each(n => mark(n, depth - 1));
}

function goToDef(event: go.InputEvent, ador: go.GraphObject){
    const node = ((ador.part as go.Adornment).adornedPart) as go.Node;
    const groupType = node.data.groupType;
    const key = node.key;
    window.javaQuery({
        request: JSON.stringify({
            type: 'goToDef',
            data: JSON.stringify({
                groupType: groupType,
                signature: key
            })
        }),
        persistent: false,
        onSuccess: function(response: string) {
            console.log(response);
        },
        onFailure: function(error_code: number, error_message: string) { }
    })
}


function makeNodes(graph: Graph){
    const nodeDataArray: INode[] = [];

    graph.metadata.vars.forEach((node, index)=>{
        const parent_idx = graph.getParent(index);
        const parent = parent_idx != -1 ?graph.metadata.get(parent_idx) : null;
        const group = parent ? parent.name : null;
        const color = node.color ? node.color : 'lightblue';
        if (group != null) {
            nodeDataArray.push({key: node.name, isGroup: node.isGroup, groupType: node.groupType, color: color, group: group, isHighlighted: false, isCollapsed: true});
        } else {
            nodeDataArray.push({key: node.name, isGroup: node.isGroup, groupType: node.groupType, color: color, isHighlighted: false, isCollapsed: true});
        }
    });

    console.log('finish nodes making');
    return nodeDataArray;
}

function makeLinks(graph: Graph){
    const linkDataArray: ILink[] = [];

    graph.graph.forEach((v,k)=>{
        v.forEach(n=>linkDataArray.push({from: graph.metadata.get(k).name, to: graph.metadata.get(n).name, color: 'black', isHighlighted: false, zOrder: 0}));
    })
    console.log('finish links making');
    return linkDataArray;
}

interface INode{
    key?: string;
    color?: string;
    isGroup?: boolean;
    group?: string;
    groupType?: string;
    isHighlighted: boolean;
    isCollapsed: boolean;
}

interface ILink{
    key?: string;
    from: string;
    to: string;
    color?: string;
    isHighlighted: boolean;
    zOrder?: number;
}