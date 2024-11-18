import { parse } from "yaml";

export class Graph{
    metadata: Metadata;
    parent: Map<number, number>;
    children: Map<number, number[]>;
    graph: Map<number, number[]>;

    constructor(data: any){
        this.metadata = new Metadata(data.metadata);
        this.parent = new Map();
        Object.entries(data.parent).forEach(entry=>{
            this.parent.set(parseInt(entry[0]), entry[1] as number);
        });
        this.children = new Map();
        Object.entries(data.children).forEach(entry=>{
            const children: number[] = [];
            (entry[1] as number[]).forEach(n=>{
                children.push(n);
            });
            this.children.set(parseInt(entry[0]), children);
        });
        this.graph = new Map();
        this.initGraph(data.graph);
    }

    initGraph(graph: any){
        Object.entries(graph).forEach(entry=>{
            const toNodess: number[] = [];
            (entry[1] as number[]).forEach(n=>{
                toNodess.push(n);
            });
            const toNodes = Array.from(new Set(toNodess));
            this.graph.set(parseInt(entry[0]), toNodes);
        });
    }

    getParent(node: number){
        // if not exist, return -1
        return this.parent.get(node) || -1;
    }
}

class Metadata{
    vars: Node[];
    groups: Map<string, number[]>;

    constructor(metadata: any){
        this.vars = [];
        metadata.vars.forEach((node: any)=>{
            this.vars.push(new Node(node));
        });
        this.groups = new Map();
        Object.entries(metadata.groups).forEach(entry=>{
            const vars: number[] = [];
            (entry[1] as number[]).forEach(n=>{
                vars.push(n);
            });
            this.groups.set(entry[0], vars);
        });
    }

    get(index: number) {
        return this.vars[index];
    }
}

class Node{
    name: string;
    isGroup: boolean;
    groupType: string;
    nodeType: string;
    color: string;

    constructor(node: any){
        this.name = node.name;
        this.isGroup = node.isGroup;
        this.groupType = node.groupType;
        this.nodeType = node.nodeType;
        this.color = node.color;
    }
}

export function build(yaml:string, visualize:(graph:Graph)=>void){
    const parsedData = parse(yaml);
    const graph:Graph = new Graph(parsedData);
    console.log('finish build, begin visualization');
    visualize(graph);
}