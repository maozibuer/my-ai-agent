import { marked } from 'marked'
import DOMPurify from 'dompurify'

// Configure marked for GitHub-flavored markdown with line breaks
marked.setOptions({
  breaks: true,
  gfm: true
})

// Parse markdown text to sanitized HTML
export function renderMarkdown(text) {
  if (!text) return ''
  const rawHtml = marked.parse(text)
  const cleanHtml = DOMPurify.sanitize(rawHtml)
  return cleanHtml
}
