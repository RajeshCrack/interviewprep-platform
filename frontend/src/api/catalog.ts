import type { CourseSummary } from './types'

/** Seed content for the mock API. It mirrors the backend's seed migration. */
export interface SeedTopic { title: string; est: number }

export const JAVA_TOPICS: SeedTopic[] = [
  { title: 'JVM, JDK and JRE', est: 45 }, { title: 'Data types and variables', est: 40 },
  { title: 'Operators and control flow', est: 40 }, { title: 'Classes and objects', est: 50 },
  { title: 'Inheritance and polymorphism', est: 55 }, { title: 'Exceptions', est: 45 },
  { title: 'Collections framework', est: 60 }, { title: 'Multithreading', est: 60 },
  { title: 'Lambdas and streams', est: 50 }, { title: 'Spring Boot basics', est: 60 },
]

export const COURSES: CourseSummary[] = [
  { slug: 'java', title: 'Java', description: 'From the JVM to Spring Boot, with a gate after every topic.', icon: 'Jv', published: true, topicCount: 10 },
  { slug: 'python', title: 'Python', description: 'Syntax, data structures and the standard library.', icon: 'Py', published: false, topicCount: 0 },
  { slug: 'dsa', title: 'Data structures and algorithms', description: 'Arrays to graphs, with timed problem sets.', icon: 'DS', published: false, topicCount: 0 },
  { slug: 'sql', title: 'SQL', description: 'Joins, windows and query design.', icon: 'Q', published: false, topicCount: 0 },
  { slug: 'os', title: 'Operating systems', description: 'Processes, memory and scheduling.', icon: 'OS', published: false, topicCount: 0 },
  { slug: 'dbms', title: 'DBMS', description: 'Normalisation, transactions and indexing.', icon: 'Db', published: false, topicCount: 0 },
  { slug: 'cn', title: 'Computer networks', description: 'TCP, HTTP and the layers in between.', icon: 'CN', published: false, topicCount: 0 },
]

export const TOPIC1 = {
  what: 'Java source code is compiled into bytecode, and a program called the JVM runs that bytecode. Three names describe how the pieces nest. The JVM runs programs. The JRE is the JVM plus the core libraries a program needs. The JDK is the JRE plus the compiler and tools you need to write programs.',
  why: 'A team can build a payroll service on Windows laptops and run the same compiled file in a Linux container in production. The JVM hides the operating system, so nothing is recompiled. It also manages memory for you, which removes a whole class of crashes common in older languages.\n\nInterviewers ask about this because it shows whether you understand what happens between pressing Run and seeing output.',
  exampleCode: '// Hello.java\npublic class Hello {\n  public static void main(String[] args) {\n    System.out.println("Payroll run started");\n  }\n}\n// javac Hello.java   creates Hello.class (bytecode)\n// java Hello         the JVM loads it, verifies it and runs it',
  hints: [
    'JDK contains JRE, and JRE contains JVM. Never the other way round.',
    'Bytecode is not machine code. The JVM, and the JIT inside it, turn it into machine code.',
    'Heap is shared by all threads. Each thread owns its own stack.',
  ],
  videoQuery: 'JVM JDK JRE explained',
}
