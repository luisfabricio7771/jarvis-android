package dev.jarvis.android.actions

import java.util.ArrayDeque

/** Main-thread queue. A cancelled, expired or superseded request cannot be approved. */
class ApprovalQueue(private val now: () -> Long = System::currentTimeMillis) {
    data class Entry(val call: ToolCall,val plan: ActionPlan.Launch,val expiresAt: Long)
    private val queue=ArrayDeque<Entry>()
    fun add(call: ToolCall,plan: ActionPlan.Launch): Boolean {
        if(queue.size>=4 || queue.any { it.call.id==call.id }) return false
        queue.addLast(Entry(call,plan,now()+90_000));return true
    }
    fun current(): Entry? = queue.peekFirst()
    fun take(id: String): Entry? {
        val first=queue.peekFirst() ?: return null
        if(first.call.id!=id || first.expiresAt<=now()) return null
        return queue.removeFirst()
    }
    fun cancel(id: String): Entry? { val value=queue.firstOrNull { it.call.id==id } ?: return null;queue.remove(value);return value }
    fun expired(): List<Entry> { val values=queue.filter { it.expiresAt<=now() };queue.removeAll(values.toSet());return values }
    fun clear(): List<Entry> = queue.toList().also { queue.clear() }
}
