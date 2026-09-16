export interface MethodologySignal {
  id: string;
  name: string;
  description: string;
}

export interface MethodologySection {
  id: string;
  title: string;
  description: string;
  formula?: string;
  signals?: MethodologySignal[];
  scoreFlow?: string[];
}

export interface MethodologySample {
  time: string;
  heapUsedMb: number;
  gcReclaimedMb: number;
}

export interface MethodologyExample {
  id: string;
  title: string;
  description: string;
  samples: MethodologySample[];
}

export interface AnalysisMethodology {
  title: string;
  version: number;
  sections: MethodologySection[];
  examples: MethodologyExample[];
}
