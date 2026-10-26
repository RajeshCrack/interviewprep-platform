import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, type McqAttempt, type McqResult } from './api'
import { mockApi } from './api/mock'
import McqPage from './pages/McqPage'
import McqResultPage from './pages/McqResultPage'
import TopicPage from './pages/TopicPage'

const TOPIC = 'topic-1'

function attempt(over: Partial<McqAttempt> = {}, selected: Record<number, number> = {}): McqAttempt {
  return {
    id: 'att-1', topicId: TOPIC, startedAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 30 * 60_000).toISOString(), total: 20,
    questions: Array.from({ length: 20 }, (_, i) => ({
      position: i + 1, kind: i < 10 ? 'THEORY' : 'CODE', prompt: `Prompt ${i + 1}`,
      codeSnippet: i === 10 ? 'int x = 1;' : null, options: [`A${i}`, `B${i}`, `C${i}`, `D${i}`], selected: selected[i + 1] ?? null,
    })),
    ...over,
  }
}

function result(score: number, over: Partial<McqResult> = {}): McqResult {
  return {
    attemptId: 'att-1', topicId: TOPIC, score, total: 20, passMark: 14, passed: score >= 14, submittedAt: new Date().toISOString(),
    xpAwarded: 0,
    groups: [
      { kind: 'THEORY', difficulty: 'EASY', correct: 5, total: 5 }, { kind: 'THEORY', difficulty: 'HARD', correct: 3, total: 5 },
      { kind: 'CODE', difficulty: 'EASY', correct: 4, total: 5 }, { kind: 'CODE', difficulty: 'HARD', correct: 1, total: 5 },
    ],
    review: [
      { position: 1, kind: 'THEORY', difficulty: 'EASY', prompt: 'Which tool compiles?', codeSnippet: null, options: ['java', 'javac', 'jar', 'javap'],
        selected: 1, correct: 1, isCorrect: true, explanation: 'javac compiles source into bytecode.' },
      { position: 2, kind: 'CODE', difficulty: 'HARD', prompt: 'What does this print?', codeSnippet: 'System.out.println(10 / 4);',
        options: ['2.5', '2', '3', 'Error'], selected: 0, correct: 1, isCorrect: false, explanation: 'Integer division drops the fraction.' },
    ],
    ...over,
  }
}

function renderTest() {
  return render(
    <MemoryRouter initialEntries={[`/topics/${TOPIC}/test`]}>
      <Routes>
        <Route path="/topics/:id/test" element={<McqPage />} />
        <Route path="/tests/:attemptId/result" element={<p>Result page</p>} />
        <Route path="/topics/:id" element={<p>Topic page</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.spyOn(api, 'saveMcqAnswer').mockResolvedValue(undefined)
})
afterEach(() => {
  vi.restoreAllMocks()
  vi.useRealTimers()
})

describe('starting the test', () => {
  it('shows the first question with a timer and a 20-button palette', async () => {
    vi.spyOn(api, 'startMcq').mockResolvedValue(attempt())
    renderTest()
    expect(await screen.findByText('Prompt 1')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Question 1 of 20' })).toBeInTheDocument()
    expect(screen.getByRole('timer')).toHaveTextContent('30:00')
    expect(within(screen.getByRole('navigation', { name: 'Jump to question' })).getAllByRole('button')).toHaveLength(20)
    expect(screen.getAllByRole('radio')).toHaveLength(4)
    expect(api.startMcq).toHaveBeenCalledWith(TOPIC)
  })

  it('resumes a running test with the answers already saved', async () => {
    vi.spyOn(api, 'startMcq').mockResolvedValue(attempt({}, { 1: 2, 2: 0, 5: 3 }))
    renderTest()
    await screen.findByText('Prompt 1')
    expect(screen.getByText('3 of 20 answered')).toBeInTheDocument()
    expect(screen.getAllByRole('radio')[2]).toHaveAttribute('aria-checked', 'true')
  })

  it.each([
    [403, 'This topic is locked'],
    [409, 'You have already passed this test'],
    [503, 'The test could not start'],
  ])('explains a %i from the server', async (status, title) => {
    vi.spyOn(api, 'startMcq').mockRejectedValue(new ApiError(status, 'Server says no.'))
    renderTest()
    expect(await screen.findByRole('heading', { name: title })).toBeInTheDocument()
    expect(screen.getByText('Server says no.')).toBeInTheDocument()
  })

  it('the in-browser stand-in refuses to run a test, because the answer key must stay on the server', async () => {
    await expect(mockApi.startMcq('java-1')).rejects.toMatchObject({ status: 501 })
  })
})

describe('answering', () => {
  it('picking an option marks it and saves it to the server', async () => {
    vi.spyOn(api, 'startMcq').mockResolvedValue(attempt())
    renderTest()
    await screen.findByText('Prompt 1')
    await userEvent.click(screen.getAllByRole('radio')[1])
    expect(screen.getAllByRole('radio')[1]).toHaveAttribute('aria-checked', 'true')
    expect(api.saveMcqAnswer).toHaveBeenCalledWith('att-1', 1, 1)
    expect(screen.getByText('1 of 20 answered')).toBeInTheDocument()
  })

  it('keys 1 to 4 pick an answer and the arrows move between questions', async () => {
    vi.spyOn(api, 'startMcq').mockResolvedValue(attempt())
    renderTest()
    await screen.findByText('Prompt 1')
    await userEvent.keyboard('3')
    expect(screen.getAllByRole('radio')[2]).toHaveAttribute('aria-checked', 'true')
    await userEvent.keyboard('{ArrowRight}')
    expect(await screen.findByText('Prompt 2')).toBeInTheDocument()
    expect(screen.getAllByRole('radio').every((r) => r.getAttribute('aria-checked') === 'false')).toBe(true)
    await userEvent.keyboard('{ArrowLeft}')
    expect(await screen.findByText('Prompt 1')).toBeInTheDocument()
    expect(screen.getAllByRole('radio')[2]).toHaveAttribute('aria-checked', 'true')
  })

  it('the palette jumps to any question', async () => {
    vi.spyOn(api, 'startMcq').mockResolvedValue(attempt())
    renderTest()
    await screen.findByText('Prompt 1')
    await userEvent.click(screen.getByRole('button', { name: 'Question 11, not answered' }))
    expect(await screen.findByText('Prompt 11')).toBeInTheDocument()
    expect(screen.getByText('int x = 1;')).toBeInTheDocument()
  })
})

describe('submitting', () => {
  it('asks before submitting, counts blanks, then sends all 20 answers and opens the result', async () => {
    vi.spyOn(api, 'startMcq').mockResolvedValue(attempt({}, { 1: 1 }))
    const submit = vi.spyOn(api, 'submitMcq').mockResolvedValue(result(14, { xpAwarded: 100 }))
    renderTest()
    await screen.findByText('Prompt 1')
    await userEvent.keyboard('2')
    await userEvent.click(screen.getByRole('button', { name: 'Submit test' }))
    const dialog = screen.getByRole('alertdialog')
    expect(dialog).toHaveTextContent('You answered 1 of 20.')
    expect(dialog).toHaveTextContent('19 left blank will count as wrong')
    expect(submit).not.toHaveBeenCalled()

    await userEvent.click(within(dialog).getByRole('button', { name: 'Keep working' }))
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Submit test' }))
    await userEvent.click(screen.getByRole('button', { name: 'Submit now' }))
    expect(await screen.findByText('Result page')).toBeInTheDocument()
    const sent = submit.mock.calls[0][1]
    expect(sent).toHaveLength(20)
    expect(sent[0]).toBe(1)
    expect(sent.slice(1).every((a) => a === null)).toBe(true)
  })

  it('sends the test by itself when the time runs out', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.spyOn(api, 'startMcq').mockResolvedValue(attempt({ expiresAt: new Date(Date.now() + 2000).toISOString() }))
    const submit = vi.spyOn(api, 'submitMcq').mockResolvedValue(result(3))
    renderTest()
    await screen.findByText('Prompt 1')
    expect(submit).not.toHaveBeenCalled()
    await act(async () => { await vi.advanceTimersByTimeAsync(3500) })
    expect(submit).toHaveBeenCalledTimes(1)
    expect(await screen.findByText('Result page')).toBeInTheDocument()
  })

  it('shows the server message when the time was already up', async () => {
    vi.spyOn(api, 'startMcq').mockResolvedValue(attempt())
    vi.spyOn(api, 'submitMcq').mockRejectedValue(new ApiError(410, 'Time is up for this test. Start a new one.'))
    renderTest()
    await screen.findByText('Prompt 1')
    await userEvent.click(screen.getByRole('button', { name: 'Submit test' }))
    await userEvent.click(screen.getByRole('button', { name: 'Submit now' }))
    expect(await screen.findByRole('heading', { name: 'Time is up' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to the topic' })).toHaveAttribute('href', `/topics/${TOPIC}`)
  })

  it('keeps the test open and shows the error when submitting fails', async () => {
    vi.spyOn(api, 'startMcq').mockResolvedValue(attempt())
    vi.spyOn(api, 'submitMcq').mockRejectedValue(new ApiError(500, 'Something went wrong. Try again.'))
    renderTest()
    await screen.findByText('Prompt 1')
    await userEvent.click(screen.getByRole('button', { name: 'Submit test' }))
    await userEvent.click(screen.getByRole('button', { name: 'Submit now' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong')
    expect(screen.getByText('Prompt 1')).toBeInTheDocument()
  })
})

describe('result page', () => {
  function renderResult(r: McqResult, state?: unknown) {
    vi.spyOn(api, 'mcqResult').mockResolvedValue(r)
    return render(
      <MemoryRouter initialEntries={[{ pathname: '/tests/att-1/result', state }]}>
        <Routes><Route path="/tests/:attemptId/result" element={<McqResultPage />} /></Routes>
      </MemoryRouter>,
    )
  }

  it('a fail shows the score, the group breakdown, the explanations and a retry link', async () => {
    renderResult(result(13))
    expect(await screen.findByRole('heading', { name: 'Not yet' })).toBeInTheDocument()
    expect(screen.getByText('You need 14 to pass and scored 13.', { exact: false })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Try again with new questions' })).toHaveAttribute('href', `/topics/${TOPIC}/test`)
    expect(screen.getByText('Code · hard')).toBeInTheDocument()
    expect(screen.getByText('Integer division drops the fraction.')).toBeVisible() // wrong answers open by default
    expect(screen.getByText('Review · 1 to learn from')).toBeInTheDocument()
    expect(screen.queryByText(/XP for passing/)).not.toBeInTheDocument()
  })

  it('a pass shows the XP award once and no retry link', async () => {
    renderResult(result(15), { xpAwarded: 100 })
    expect(await screen.findByRole('heading', { name: 'Passed' })).toBeInTheDocument()
    expect(screen.getByText('+100 XP for passing')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Try again/ })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Continue' })).toHaveAttribute('href', `/topics/${TOPIC}`)
  })

  it('marks which option was right and which one the student picked', async () => {
    renderResult(result(13))
    await screen.findByRole('heading', { name: 'Not yet' })
    expect(screen.getAllByText('Your answer')).toHaveLength(2) // one right pick, one wrong pick
    expect(screen.getAllByText('Right answer').length).toBeGreaterThanOrEqual(2)
  })

  it('says so when the test has not been submitted yet', async () => {
    vi.spyOn(api, 'mcqResult').mockRejectedValue(new ApiError(409, 'This test has not been submitted.'))
    render(
      <MemoryRouter initialEntries={['/tests/att-1/result']}>
        <Routes><Route path="/tests/:attemptId/result" element={<McqResultPage />} /></Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByRole('heading', { name: 'This test is still running' })).toBeInTheDocument()
  })
})

describe('topic page', () => {
  it('offers the test on an open topic', async () => {
    await mockApi.register('Test Student', 'a@college.edu', 'password1')
    render(
      <MemoryRouter initialEntries={['/topics/java-1']}>
        <Routes><Route path="/topics/:id" element={<TopicPage />} /></Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByRole('link', { name: 'Start the test' })).toHaveAttribute('href', '/topics/java-1/test')
  })
})
