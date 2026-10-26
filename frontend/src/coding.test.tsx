import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, type CodingRound, type CodingSubmission } from './api'
import { mockApi } from './api/mock'
import CodingPage from './pages/CodingPage'

const TOPIC = 'topic-1'

function round(over: Partial<CodingRound> = {}): CodingRound {
  const mk = (n: number, difficulty: 'EASY' | 'MEDIUM' | 'HARD', title: string) => ({
    id: `p${n}`, position: n, difficulty, title, statement: `Statement ${n}.\n\nSecond paragraph ${n}.`,
    starterCode: `class Main { /* starter ${n} */ }`, samples: [{ input: `in${n}`, expected: `out${n}` }], solved: false, lastCode: null,
  })
  return {
    topicId: TOPIC, passed: false, solvedCount: 0, totalProblems: 5, solvedEasy: 0, totalEasy: 2, neededTotal: 3,
    problems: [mk(1, 'EASY', 'Payroll total'), mk(2, 'EASY', 'Count the vowels'), mk(3, 'MEDIUM', 'Reverse the words'),
      mk(4, 'HARD', 'Longest unique stretch'), mk(5, 'HARD', 'Merge meeting slots')],
    ...over,
  }
}

function sub(over: Partial<CodingSubmission> = {}): CodingSubmission {
  return {
    id: 's1', mode: 'RUN', status: 'ACCEPTED', passedTests: 1, totalTests: 1, message: null,
    tests: [{ position: 1, visible: true, passed: true, status: 'PASSED', input: 'in1', expected: 'out1', actual: 'out1', error: null }],
    solved: false, roundPassed: false, xpAwarded: 0, ...over,
  }
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={[`/topics/${TOPIC}/coding`]}>
      <Routes>
        <Route path="/topics/:id/coding" element={<CodingPage />} />
        <Route path="/topics/:id/test" element={<p>MCQ page</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

afterEach(() => vi.restoreAllMocks())

describe('opening the round', () => {
  it('shows five problem tabs, the first problem, its sample and the pass rule', async () => {
    vi.spyOn(api, 'codingRound').mockResolvedValue(round())
    renderPage()
    expect(await screen.findByRole('heading', { name: 'Payroll total' })).toBeInTheDocument()
    expect(screen.getAllByRole('tab')).toHaveLength(5)
    expect(screen.getByRole('tab', { selected: true })).toHaveTextContent('Payroll total')
    expect(screen.getByText('Second paragraph 1.')).toBeInTheDocument()
    expect(screen.getByText('out1')).toBeInTheDocument()
    expect(screen.getByText('0 of 5 solved')).toBeInTheDocument()
    expect(screen.getByText(/3 solved, including both easy problems \(0 of 2 easy so far\)/)).toBeInTheDocument()
    expect(screen.getByLabelText('Java code')).toHaveValue('class Main { /* starter 1 */ }')
  })

  it('brings back the last code the student wrote', async () => {
    const r = round()
    r.problems[0].lastCode = 'my earlier attempt'
    vi.spyOn(api, 'codingRound').mockResolvedValue(r)
    renderPage()
    expect(await screen.findByLabelText('Java code')).toHaveValue('my earlier attempt')
  })

  it('explains that the round is closed until the MCQ test is passed', async () => {
    vi.spyOn(api, 'codingRound').mockRejectedValue(new ApiError(403, 'Pass the MCQ test for this topic to open the coding round.'))
    renderPage()
    expect(await screen.findByRole('heading', { name: 'The coding round is closed' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('link', { name: 'Go to the MCQ test' }))
    expect(await screen.findByText('MCQ page')).toBeInTheDocument()
  })

  it('the in-browser stand-in refuses to judge code', async () => {
    await expect(mockApi.submitCode('p1', 'x', 'RUN')).rejects.toMatchObject({ status: 501 })
  })
})

describe('writing and running code', () => {
  it('keeps each problem its own code when switching tabs', async () => {
    vi.spyOn(api, 'codingRound').mockResolvedValue(round())
    renderPage()
    const editor = await screen.findByLabelText('Java code')
    await userEvent.clear(editor)
    await userEvent.type(editor, 'first')
    await userEvent.click(screen.getByRole('tab', { name: /Count the vowels/ }))
    expect(screen.getByLabelText('Java code')).toHaveValue('class Main { /* starter 2 */ }')
    await userEvent.click(screen.getByRole('tab', { name: /Payroll total/ }))
    expect(screen.getByLabelText('Java code')).toHaveValue('first')
  })

  it('Tab indents by four spaces, and Escape then Tab leaves the editor', async () => {
    vi.spyOn(api, 'codingRound').mockResolvedValue(round())
    renderPage()
    const editor = await screen.findByLabelText('Java code')
    await userEvent.clear(editor)
    await userEvent.type(editor, 'a')
    await userEvent.keyboard('{Tab}b')
    expect(editor).toHaveValue('a    b')
    await userEvent.keyboard('{Escape}{Tab}')
    expect(editor).not.toHaveFocus()
  })

  it('Run sends the code in RUN mode and shows the result without solving the problem', async () => {
    vi.spyOn(api, 'codingRound').mockResolvedValue(round())
    const send = vi.spyOn(api, 'submitCode').mockResolvedValue(sub())
    renderPage()
    await screen.findByRole('heading', { name: 'Payroll total' })
    await userEvent.click(screen.getByRole('button', { name: 'Run samples' }))
    const box = await screen.findByRole('region', { name: 'Result' })
    expect(send).toHaveBeenCalledWith('p1', 'class Main { /* starter 1 */ }', 'RUN')
    expect(within(box).getByText('Accepted')).toBeInTheDocument()
    expect(within(box).getByText(/1 of 1 tests passed \(samples only\)/)).toBeInTheDocument()
    expect(screen.queryByLabelText('solved')).not.toBeInTheDocument()
  })

  it('a wrong answer shows input, expected and actual output for a sample test', async () => {
    vi.spyOn(api, 'codingRound').mockResolvedValue(round())
    vi.spyOn(api, 'submitCode').mockResolvedValue(sub({
      status: 'WRONG_ANSWER', passedTests: 0, totalTests: 1,
      tests: [{ position: 1, visible: true, passed: false, status: 'WRONG_ANSWER', input: '3\n1 2 3', expected: '6', actual: '5', error: null }],
    }))
    renderPage()
    await screen.findByRole('heading', { name: 'Payroll total' })
    await userEvent.click(screen.getByRole('button', { name: 'Run samples' }))
    const box = await screen.findByRole('region', { name: 'Result' })
    expect(within(box).getByText('Wrong answer')).toBeInTheDocument()
    expect(within(box).getByText('Your output')).toBeInTheDocument()
    expect(within(box).getByText('5')).toBeInTheDocument()
    expect(within(box).getByText('6')).toBeInTheDocument()
  })

  it('a hidden test failure shows only that it failed, never its data', async () => {
    vi.spyOn(api, 'codingRound').mockResolvedValue(round())
    vi.spyOn(api, 'submitCode').mockResolvedValue(sub({
      mode: 'SUBMIT', status: 'WRONG_ANSWER', passedTests: 1, totalTests: 2,
      tests: [
        { position: 1, visible: true, passed: true, status: 'PASSED', input: 'in1', expected: 'out1', actual: 'out1', error: null },
        { position: 2, visible: false, passed: false, status: 'WRONG_ANSWER', input: null, expected: null, actual: null, error: null },
      ],
    }))
    renderPage()
    await screen.findByRole('heading', { name: 'Payroll total' })
    await userEvent.click(screen.getByRole('button', { name: 'Submit' }))
    const box = await screen.findByRole('region', { name: 'Result' })
    expect(within(box).getByText(/Hidden test 2/)).toBeInTheDocument()
    expect(within(box).queryByText('Your output')).not.toBeInTheDocument()
    expect(within(box).queryByText('Expected')).not.toBeInTheDocument()
  })

  it('a compile error shows the compiler message', async () => {
    vi.spyOn(api, 'codingRound').mockResolvedValue(round())
    vi.spyOn(api, 'submitCode').mockResolvedValue(sub({
      status: 'COMPILE_ERROR', passedTests: 0, totalTests: 1, message: "Main.java:3: error: ';' expected",
      tests: [{ position: 1, visible: true, passed: false, status: 'NOT_RUN', input: null, expected: null, actual: null, error: null }],
    }))
    renderPage()
    await screen.findByRole('heading', { name: 'Payroll total' })
    await userEvent.click(screen.getByRole('button', { name: 'Run samples' }))
    expect(await screen.findByText("Main.java:3: error: ';' expected")).toBeInTheDocument()
    expect(screen.getByText('Compile error')).toBeInTheDocument()
  })

  it('shows the server message when judging is busy or unavailable', async () => {
    vi.spyOn(api, 'codingRound').mockResolvedValue(round())
    vi.spyOn(api, 'submitCode').mockRejectedValue(new ApiError(429, 'Slow down: at most 10 runs a minute.'))
    renderPage()
    await screen.findByRole('heading', { name: 'Payroll total' })
    await userEvent.click(screen.getByRole('button', { name: 'Run samples' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Slow down')
    expect(screen.getByRole('button', { name: 'Submit' })).toBeEnabled() // not stuck on "Judging"
  })

  it('Reset code restores the starter code', async () => {
    vi.spyOn(api, 'codingRound').mockResolvedValue(round())
    renderPage()
    const editor = await screen.findByLabelText('Java code')
    await userEvent.clear(editor)
    await userEvent.type(editor, 'junk')
    await userEvent.click(screen.getByRole('button', { name: 'Reset code' }))
    expect(screen.getByLabelText('Java code')).toHaveValue('class Main { /* starter 1 */ }')
  })
})

describe('solving and passing the round', () => {
  it('a solved problem gets a tick and updates the progress counts', async () => {
    vi.spyOn(api, 'codingRound').mockResolvedValue(round())
    vi.spyOn(api, 'submitCode').mockResolvedValue(sub({ mode: 'SUBMIT', solved: true }))
    renderPage()
    await screen.findByRole('heading', { name: 'Payroll total' })
    await userEvent.click(screen.getByRole('button', { name: 'Submit' }))
    expect(await screen.findByText('Problem solved')).toBeInTheDocument()
    expect(within(screen.getByRole('tab', { name: /Payroll total/ })).getByLabelText('solved')).toBeInTheDocument()
    expect(screen.getByText('1 of 5 solved')).toBeInTheDocument()
    expect(screen.getByText(/1 of 2 easy so far/)).toBeInTheDocument()
  })

  it('passing the round shows the XP award and a link to the roadmap', async () => {
    const r = round({ solvedCount: 2, solvedEasy: 2 })
    r.problems[0].solved = true; r.problems[1].solved = true
    vi.spyOn(api, 'codingRound').mockResolvedValue(r)
    vi.spyOn(api, 'submitCode').mockResolvedValue(sub({ mode: 'SUBMIT', solved: true, roundPassed: true, xpAwarded: 200 }))
    renderPage()
    await userEvent.click(await screen.findByRole('tab', { name: /Reverse the words/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Submit' }))
    const banner = await screen.findByRole('status')
    expect(banner).toHaveTextContent('+200 XP')
    expect(within(banner).getByRole('link', { name: 'See the roadmap' })).toHaveAttribute('href', '/courses/java')
    expect(screen.getByText('Passed')).toBeInTheDocument()
  })
})
