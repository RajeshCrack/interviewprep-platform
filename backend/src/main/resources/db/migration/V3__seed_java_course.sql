-- Seed data: the course catalog and the Java roadmap. Only topic 1 has full lesson content so far.

INSERT INTO courses (id, slug, title, description, icon, published, position) VALUES ('39c958f9-4529-5f31-b888-e2db8a3d5d42', 'java', 'Java', 'From the JVM to Spring Boot, with a gate after every topic.', 'Jv', TRUE, 1);
INSERT INTO courses (id, slug, title, description, icon, published, position) VALUES ('1bea668d-3293-5292-b0b6-fd9f19f0320b', 'python', 'Python', 'Syntax, data structures and the standard library.', 'Py', FALSE, 2);
INSERT INTO courses (id, slug, title, description, icon, published, position) VALUES ('911d56a0-a3b9-5671-aa59-1651112003fc', 'dsa', 'Data structures and algorithms', 'Arrays to graphs, with timed problem sets.', 'DS', FALSE, 3);
INSERT INTO courses (id, slug, title, description, icon, published, position) VALUES ('8cef4634-62da-56ff-9b54-9f782dba80ec', 'sql', 'SQL', 'Joins, windows and query design.', 'Q', FALSE, 4);
INSERT INTO courses (id, slug, title, description, icon, published, position) VALUES ('44187f46-0af8-56b8-bc37-a43487b07897', 'os', 'Operating systems', 'Processes, memory and scheduling.', 'OS', FALSE, 5);
INSERT INTO courses (id, slug, title, description, icon, published, position) VALUES ('6335b9e3-97ca-5230-a7c5-2d383b26cf4d', 'dbms', 'DBMS', 'Normalisation, transactions and indexing.', 'Db', FALSE, 6);
INSERT INTO courses (id, slug, title, description, icon, published, position) VALUES ('95af8d30-4f79-544d-9f53-b9f9b755c19d', 'cn', 'Computer networks', 'TCP, HTTP and the layers in between.', 'CN', FALSE, 7);

INSERT INTO topics (id, course_id, position, title, est_minutes, video_query, what_text, why_text, example_code, hints, has_content) VALUES ('480e01d7-ca63-5c82-8c09-a7b61243756e', '39c958f9-4529-5f31-b888-e2db8a3d5d42', 1, 'JVM, JDK and JRE', 45, 'JVM JDK JRE explained', 'Java source code is compiled into bytecode, and a program called the JVM runs that bytecode. Three names describe how the pieces nest. The JVM runs programs. The JRE is the JVM plus the core libraries a program needs. The JDK is the JRE plus the compiler and tools you need to write programs.', 'A team can build a payroll service on Windows laptops and run the same compiled file in a Linux container in production. The JVM hides the operating system, so nothing is recompiled. It also manages memory for you, which removes a whole class of crashes common in older languages.

Interviewers ask about this because it shows whether you understand what happens between pressing Run and seeing output.', '// Hello.java
public class Hello {
  public static void main(String[] args) {
    System.out.println("Payroll run started");
  }
}
// javac Hello.java   creates Hello.class (bytecode)
// java Hello         the JVM loads it, verifies it and runs it', 'JDK contains JRE, and JRE contains JVM. Never the other way round.
Bytecode is not machine code. The JVM, and the JIT inside it, turn it into machine code.
Heap is shared by all threads. Each thread owns its own stack.', TRUE);
INSERT INTO topics (id, course_id, position, title, est_minutes, video_query, what_text, why_text, example_code, hints, has_content) VALUES ('b673f0fe-e009-56d1-8ce5-ab16971e6b26', '39c958f9-4529-5f31-b888-e2db8a3d5d42', 2, 'Data types and variables', 40, 'Java data types and variables explained', NULL, NULL, NULL, NULL, FALSE);
INSERT INTO topics (id, course_id, position, title, est_minutes, video_query, what_text, why_text, example_code, hints, has_content) VALUES ('0349d7dd-9a56-52b4-b0db-50361515a3be', '39c958f9-4529-5f31-b888-e2db8a3d5d42', 3, 'Operators and control flow', 40, 'Java operators and control flow explained', NULL, NULL, NULL, NULL, FALSE);
INSERT INTO topics (id, course_id, position, title, est_minutes, video_query, what_text, why_text, example_code, hints, has_content) VALUES ('b68961e6-19f7-5d49-840b-61066c4e8f8f', '39c958f9-4529-5f31-b888-e2db8a3d5d42', 4, 'Classes and objects', 50, 'Java classes and objects explained', NULL, NULL, NULL, NULL, FALSE);
INSERT INTO topics (id, course_id, position, title, est_minutes, video_query, what_text, why_text, example_code, hints, has_content) VALUES ('2a20a9fb-8a78-53f4-8f36-a81410aa25c7', '39c958f9-4529-5f31-b888-e2db8a3d5d42', 5, 'Inheritance and polymorphism', 55, 'Java inheritance and polymorphism explained', NULL, NULL, NULL, NULL, FALSE);
INSERT INTO topics (id, course_id, position, title, est_minutes, video_query, what_text, why_text, example_code, hints, has_content) VALUES ('6a2304e8-f629-5758-b4fe-ec7f085d9a06', '39c958f9-4529-5f31-b888-e2db8a3d5d42', 6, 'Exceptions', 45, 'Java exceptions explained', NULL, NULL, NULL, NULL, FALSE);
INSERT INTO topics (id, course_id, position, title, est_minutes, video_query, what_text, why_text, example_code, hints, has_content) VALUES ('6b63fad4-7261-5251-86db-d29f4fc1a4bd', '39c958f9-4529-5f31-b888-e2db8a3d5d42', 7, 'Collections framework', 60, 'Java collections framework explained', NULL, NULL, NULL, NULL, FALSE);
INSERT INTO topics (id, course_id, position, title, est_minutes, video_query, what_text, why_text, example_code, hints, has_content) VALUES ('9e90e764-1be8-5b4d-9de3-8c1b82e58e1e', '39c958f9-4529-5f31-b888-e2db8a3d5d42', 8, 'Multithreading', 60, 'Java multithreading explained', NULL, NULL, NULL, NULL, FALSE);
INSERT INTO topics (id, course_id, position, title, est_minutes, video_query, what_text, why_text, example_code, hints, has_content) VALUES ('6f7bfc83-3ff8-5bc8-9e7f-2d4874181a11', '39c958f9-4529-5f31-b888-e2db8a3d5d42', 9, 'Lambdas and streams', 50, 'Java lambdas and streams explained', NULL, NULL, NULL, NULL, FALSE);
INSERT INTO topics (id, course_id, position, title, est_minutes, video_query, what_text, why_text, example_code, hints, has_content) VALUES ('fab4f4c7-f3a8-5017-b675-9b0978eaa29a', '39c958f9-4529-5f31-b888-e2db8a3d5d42', 10, 'Spring Boot basics', 60, 'Java spring boot basics explained', NULL, NULL, NULL, NULL, FALSE);
