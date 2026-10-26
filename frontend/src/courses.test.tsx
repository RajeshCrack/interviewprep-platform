import { render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { mockApi, mockPassTopic } from './api/mock'
import CoursesPage from './pages/CoursesPage'
import RoadmapPage from './pages/RoadmapPage'
import TopicPage from './pages/TopicPage'

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/courses" element={<CoursesPage />} />
        <Route path="/courses/:slug" element={<RoadmapPage />} />
        <Route path="/topics/:id" element={<TopicPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

async function student(email = 'a@college.edu') {
  await mockApi.register('Test Student', email, 'password1')
}

describe('course catalog', () => {
  it('opens Java and marks the other six courses as coming soon', async () => {
    await student()
    renderAt('/courses')
    expect(await screen.findByRole('link', { name: 'Open roadmap' })).toHaveAttribute('href', '/courses/java')
    expect(screen.getAllByText('Coming soon')).toHaveLength(6)
  })
})

describe('roadmap', () => {
  it('shows topic 1 as up next and every later topic locked', async () => {
    await student()
    renderAt('/courses/java')
    expect(await screen.findByText('0 of 10 topics passed')).toBeInTheDocument()
    const items = screen.getAllByRole('listitem')
    expect(items).toHaveLength(10)
    expect(items[0]).toHaveAttribute('data-status', 'CURRENT')
    expect(within(items[0]).getByText('Up next')).toBeInTheDocument()
    for (const item of items.slice(1)) expect(item).toHaveAttribute('data-status', 'LOCKED')
    expect(within(items[1]).getByText('Pass topic 1 to unlock')).toBeInTheDocument()
  })

  it('does not link a locked topic, so it cannot be opened from the page', async () => {
    await student()
    renderAt('/courses/java')
    await screen.findByText('0 of 10 topics passed')
    const items = screen.getAllByRole('listitem')
    expect(within(items[0]).getByRole('link')).toBeInTheDocument()
    expect(within(items[1]).queryByRole('link')).toBeNull()
    expect(within(items[1]).getByRole('group', { name: 'Data types and variables, locked' })).toBeInTheDocument()
  })

  it('unlocks the next topic once a topic is passed', async () => {
    await student()
    mockPassTopic(1)
    renderAt('/courses/java')
    expect(await screen.findByText('1 of 10 topics passed')).toBeInTheDocument()
    const items = screen.getAllByRole('listitem')
    expect(items.map((i) => i.getAttribute('data-status')).slice(0, 3)).toEqual(['PASSED', 'CURRENT', 'LOCKED'])
    expect(within(items[1]).getByRole('link')).toBeInTheDocument()
  })

  it('shows an error for a course that is not open yet', async () => {
    await student()
    renderAt('/courses/python')
    expect(await screen.findByRole('alert')).toHaveTextContent('This course is not available yet.')
  })

  it('keeps one student\'s progress away from another', async () => {
    await student('first@college.edu')
    mockPassTopic(1)
    await mockApi.logout()
    await student('second@college.edu')
    renderAt('/courses/java')
    expect(await screen.findByText('0 of 10 topics passed')).toBeInTheDocument()
  })
})

describe('topic page', () => {
  it('shows the lesson for topic 1 with its sections, hints and video link', async () => {
    await student()
    renderAt('/topics/java-1')
    expect(await screen.findByRole('heading', { name: 'JVM, JDK and JRE' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'What is it?' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Why do we need it?' })).toBeInTheDocument()
    expect(screen.getByText(/public class Hello/)).toBeInTheDocument()
    expect(screen.getAllByRole('listitem')).toHaveLength(3)
    const video = screen.getByRole('link', { name: 'Watch top-viewed videos' })
    expect(video).toHaveAttribute('href', expect.stringContaining('youtube.com/results'))
    expect(video).toHaveAttribute('rel', expect.stringContaining('noopener'))
  })

  it('locks the coding round until the MCQ test is passed', async () => {
    await student()
    renderAt('/topics/java-1')
    expect(await screen.findByRole('button', { name: 'Pass the MCQ test first' })).toBeDisabled()
  })

  it('refuses to open a locked topic and says why', async () => {
    await student()
    renderAt('/topics/java-2')
    const box = await screen.findByRole('status')
    expect(box).toHaveTextContent('This topic is locked')
    expect(box).toHaveTextContent('Pass the previous topic to unlock this one.')
    expect(screen.queryByRole('heading', { name: 'Data types and variables' })).toBeNull()
  })

  it('opens topic 2 after topic 1 is passed, with a lesson-coming-soon notice', async () => {
    await student()
    mockPassTopic(1)
    renderAt('/topics/java-2')
    expect(await screen.findByRole('heading', { name: 'Data types and variables' })).toBeInTheDocument()
    expect(screen.getByText('Lesson coming soon')).toBeInTheDocument()
  })

  it('reports an unknown topic', async () => {
    await student()
    renderAt('/topics/nope')
    expect(await screen.findByRole('alert')).toHaveTextContent('Topic not found.')
  })
})

it('a signed-out visitor cannot read the catalog', async () => {
  await mockApi.logout()
  await expect(mockApi.courses()).rejects.toThrow('Please log in.')
})
